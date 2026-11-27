package com.fishing.logbook.upload

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

data class UploadResponse(val url: String)

/**
 * 사진 업로드 (MVP: 로컬 디스크 저장).
 * 운영 전환 시 이 컨트롤러만 S3 PutObject로 교체하면 됩니다.
 */
@RestController
@RequestMapping("/api/uploads")
class UploadController(
    @Value("\${app.upload-dir}") private val uploadDir: String,
) {
    @PostMapping
    fun upload(@RequestParam("file") file: MultipartFile): UploadResponse {
        require(!file.isEmpty) { "빈 파일입니다" }
        val dir: Path = Paths.get(uploadDir).toAbsolutePath()
        Files.createDirectories(dir)

        val ext = file.originalFilename?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
        val filename = UUID.randomUUID().toString() + (ext?.let { ".$it" } ?: "")
        val target = dir.resolve(filename)
        file.inputStream.use { Files.copy(it, target) }

        // 정적 리소스로 서빙되는 경로 (WebConfig 참고)
        return UploadResponse(url = "/uploads/$filename")
    }
}
