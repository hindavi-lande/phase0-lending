package com.example.lending.borrower;

import com.example.lending.borrower.dto.BorrowerRequest;
import com.example.lending.borrower.dto.BorrowerResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/borrowers")
public class BorrowerController {

    private final BorrowerService borrowerService;

    public BorrowerController(BorrowerService borrowerService) {
        this.borrowerService = borrowerService;
    }

    @PostMapping
    public ResponseEntity<BorrowerResponse> create(@Valid @RequestBody BorrowerRequest request) {
        BorrowerResponse created = borrowerService.create(request);
        return ResponseEntity.created(URI.create("/api/borrowers/" + created.id())).body(created);
    }

    @GetMapping
    public List<BorrowerResponse> list() {
        return borrowerService.list();
    }

    @GetMapping("/{id}")
    public BorrowerResponse get(@PathVariable UUID id) {
        return borrowerService.getById(id);
    }

    @PutMapping("/{id}")
    public BorrowerResponse update(@PathVariable UUID id, @Valid @RequestBody BorrowerRequest request) {
        return borrowerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        borrowerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
