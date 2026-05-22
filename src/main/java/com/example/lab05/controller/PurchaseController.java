package com.example.lab05.controller;

import com.example.lab05.dto.PurchaseRequest;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.service.PurchaseService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/22-01409/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public PurchaseReceipt purchase(@RequestBody PurchaseRequest request) {
        return purchaseService.executePurchase(request);
    }

    @GetMapping("/person/{personName}")
    public List<PurchaseReceipt> getPurchasesByPerson(@PathVariable String personName) {
        return purchaseService.getPurchasesByPerson(personName);
    }
}