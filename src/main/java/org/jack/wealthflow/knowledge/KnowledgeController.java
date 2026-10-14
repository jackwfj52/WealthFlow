package org.jack.wealthflow.knowledge;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import static org.jack.wealthflow.knowledge.KnowledgeDtos.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledge;
    private final KnowledgeSearchService search;
    private final KnowledgeIndexService index;
    @GetMapping("/documents") public ApiResponse<Page> list(@RequestParam(required=false) String query,
            @RequestParam(required=false) String type,@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page) {
        return ApiResponse.success(knowledge.list(query,type,status,page));
    }
    @GetMapping("/documents/{id}") public ApiResponse<Document> get(@PathVariable String id) { return ApiResponse.success(knowledge.get(id)); }
    @PostMapping("/documents") public ApiResponse<Document> create(@RequestBody Write value) { return ApiResponse.success(knowledge.create(value)); }
    @PutMapping("/documents/{id}") public ApiResponse<Document> update(@PathVariable String id,@RequestBody Write value) { return ApiResponse.success(knowledge.update(id,value)); }
    @PostMapping("/documents/{id}/status") public ApiResponse<Document> status(@PathVariable String id,@RequestBody Status value) { return ApiResponse.success(knowledge.status(id,value)); }
    @GetMapping("/documents/{id}/versions") public ApiResponse<List<Version>> versions(@PathVariable String id) { return ApiResponse.success(knowledge.versions(id)); }
    @DeleteMapping("/documents/{id}") public ResponseEntity<Void> delete(@PathVariable String id) { knowledge.delete(id); return ResponseEntity.noContent().build(); }
    @PostMapping("/imports") public ApiResponse<ImportResult> imports(@RequestBody Imports value) { return ApiResponse.success(knowledge.importDocuments(value)); }
    @PostMapping("/search") public ApiResponse<SearchResult> search(@RequestBody Search value) { return ApiResponse.success(search.search(value)); }
    @GetMapping("/index-status") public ApiResponse<IndexStatus> index() { return ApiResponse.success(index.status()); }
    @PostMapping("/reindex") public ApiResponse<IndexStatus> reindex() { return ApiResponse.success(index.reindex()); }
}
