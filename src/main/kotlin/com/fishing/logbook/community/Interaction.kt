package com.fishing.logbook.community

import com.fishing.logbook.domain.Comment
import com.fishing.logbook.domain.TripLike
import com.fishing.logbook.repository.CommentRepository
import com.fishing.logbook.repository.TripLikeRepository
import com.fishing.logbook.repository.UserRepository
import com.fishing.logbook.security.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

// ---------- 좋아요 ----------

data class LikeResult(val liked: Boolean, val likeCount: Long)

@Service
class LikeService(private val tripLikeRepository: TripLikeRepository) {

    @Transactional
    fun like(userId: Long, tripId: Long): LikeResult {
        if (!tripLikeRepository.existsByUserIdAndTripId(userId, tripId)) {
            tripLikeRepository.save(TripLike(userId = userId, tripId = tripId))
        }
        return LikeResult(true, tripLikeRepository.countByTripId(tripId))
    }

    @Transactional
    fun unlike(userId: Long, tripId: Long): LikeResult {
        tripLikeRepository.deleteByUserIdAndTripId(userId, tripId)
        return LikeResult(false, tripLikeRepository.countByTripId(tripId))
    }
}

// ---------- 댓글 ----------

data class CommentRequest(@field:NotBlank @field:Size(max = 1000) val content: String)

data class CommentResponse(
    val id: Long,
    val authorId: Long,
    val authorNickname: String,
    val content: String,
    val createdAt: Instant,
)

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun add(userId: Long, tripId: Long, req: CommentRequest): CommentResponse {
        val saved = commentRepository.save(Comment(tripId = tripId, userId = userId, content = req.content))
        val nickname = userRepository.findByIdOrNull(userId)?.nickname ?: "알수없음"
        return CommentResponse(saved.id!!, userId, nickname, saved.content, saved.createdAt)
    }

    @Transactional(readOnly = true)
    fun list(tripId: Long): List<CommentResponse> {
        val comments = commentRepository.findByTripIdOrderByCreatedAtAsc(tripId)
        if (comments.isEmpty()) return emptyList()
        val nicknames = userRepository.findAllById(comments.map { it.userId })
            .associate { it.id to it.nickname }
        return comments.map {
            CommentResponse(
                id = it.id!!,
                authorId = it.userId,
                authorNickname = nicknames[it.userId] ?: "알수없음",
                content = it.content,
                createdAt = it.createdAt,
            )
        }
    }

    @Transactional
    fun delete(userId: Long, commentId: Long) {
        val comment = commentRepository.findByIdOrNull(commentId)
            ?: throw NoSuchElementException("댓글을 찾을 수 없습니다")
        require(comment.userId == userId) { "본인 댓글만 삭제할 수 있습니다" }
        commentRepository.delete(comment)
    }
}

// ---------- 컨트롤러 ----------

@RestController
@RequestMapping("/api/trips/{tripId}")
class TripInteractionController(
    private val likeService: LikeService,
    private val commentService: CommentService,
) {
    @PostMapping("/like")
    fun like(@PathVariable tripId: Long): LikeResult = likeService.like(CurrentUser.id(), tripId)

    @DeleteMapping("/like")
    fun unlike(@PathVariable tripId: Long): LikeResult = likeService.unlike(CurrentUser.id(), tripId)

    @GetMapping("/comments")
    fun comments(@PathVariable tripId: Long): List<CommentResponse> = commentService.list(tripId)

    @PostMapping("/comments")
    fun addComment(
        @PathVariable tripId: Long,
        @Valid @RequestBody req: CommentRequest,
    ): CommentResponse = commentService.add(CurrentUser.id(), tripId, req)
}

@RestController
@RequestMapping("/api/comments")
class CommentController(private val commentService: CommentService) {
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = commentService.delete(CurrentUser.id(), id)
}
