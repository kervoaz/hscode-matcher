package com.geodis.hs.matcher.api;

import com.geodis.hs.matcher.api.dto.AdminReloadResponse;
import com.geodis.hs.matcher.config.NomenclatureAdminProperties;
import com.geodis.hs.matcher.config.NomenclatureSearchRuntime;
import com.geodis.hs.matcher.embed.ChapterEmbeddingIndex;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminReloadController {

    private final NomenclatureSearchRuntime searchRuntime;
    private final NomenclatureAdminProperties adminProperties;
    private final ChapterEmbeddingIndex chapterEmbeddingIndex;

    public AdminReloadController(
            NomenclatureSearchRuntime searchRuntime,
            NomenclatureAdminProperties adminProperties,
            ChapterEmbeddingIndex chapterEmbeddingIndex) {
        this.searchRuntime = searchRuntime;
        this.adminProperties = adminProperties;
        this.chapterEmbeddingIndex = chapterEmbeddingIndex;
    }

    @PostMapping("/reload")
    public ResponseEntity<AdminReloadResponse> reload(
            @RequestHeader(value = "X-Reload-Token", required = false) String reloadToken) {
        String required = adminProperties.getReloadToken();
        if (required != null && !required.isBlank()) {
            if (reloadToken == null || !required.equals(reloadToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(AdminReloadResponse.failed("Invalid or missing X-Reload-Token"));
            }
        }
        try {
            searchRuntime.reload();
            chapterEmbeddingIndex.invalidate();
            return ResponseEntity.ok(AdminReloadResponse.reloaded(searchRuntime.anyLanguageReady()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AdminReloadResponse.failed(e.getMessage()));
        }
    }
}
