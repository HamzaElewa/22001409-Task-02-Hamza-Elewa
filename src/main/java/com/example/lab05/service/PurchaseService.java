package com.example.lab05.service;

import com.example.lab05.dto.PurchaseRequest;
import com.example.lab05.model.Product;
import com.example.lab05.model.cassandra.SensorReading;
import com.example.lab05.model.cassandra.SensorReadingKey;
import com.example.lab05.model.elastic.ProductDocument;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.repository.mongo.PurchaseReceiptRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final ProductService productService;
    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final SocialGraphService socialGraphService;
    private final SensorService sensorService;
    private final ProductSearchService productSearchService;
    private final RedisTemplate<String, Object> redisTemplate;

    public PurchaseService(
            ProductService productService,
            PurchaseReceiptRepository purchaseReceiptRepository,
            SocialGraphService socialGraphService,
            SensorService sensorService,
            ProductSearchService productSearchService,
            RedisTemplate<String, Object> redisTemplate
    ) {
        this.productService = productService;
        this.purchaseReceiptRepository = purchaseReceiptRepository;
        this.socialGraphService = socialGraphService;
        this.sensorService = sensorService;
        this.productSearchService = productSearchService;
        this.redisTemplate = redisTemplate;
    }

    public PurchaseReceipt executePurchase(PurchaseRequest request) {

        Product product = productService.getProductById(request.productId());

        if (product.getStockQuantity() < request.quantity()) {
            throw new RuntimeException("Insufficient stock for product: " + product.getName());
        }

        product.setStockQuantity(product.getStockQuantity() - request.quantity());
        Product updatedProduct = productService.updateProduct(product.getId(), product);

        PurchaseReceipt receipt = new PurchaseReceipt(
                request.personName(),
                updatedProduct.getName(),
                updatedProduct.getCategory(),
                request.quantity(),
                updatedProduct.getPrice(),
                request.purchaseDetails()
        );

        PurchaseReceipt savedReceipt = purchaseReceiptRepository.save(receipt);

        try {
            socialGraphService.purchase(
                    request.personName(),
                    updatedProduct.getName(),
                    request.quantity(),
                    updatedProduct.getPrice()
            );
        } catch (Exception e) {
            log.warn("Failed to create PURCHASED edge for {} -> {}: {}",
                    request.personName(),
                    updatedProduct.getName(),
                    e.getMessage());
        }

        try {
            SensorReading event = new SensorReading();
            SensorReadingKey key = new SensorReadingKey();
            key.setSensorId("user-activity-" + request.personName().toLowerCase());
            key.setReadingTime(Instant.now());
            event.setKey(key);
            event.setTemperature(request.quantity().doubleValue());
            event.setHumidity(0.0);
            event.setLocation("PURCHASE");
            sensorService.recordReading(event);
        } catch (Exception e) {
            log.warn("Failed to log purchase event for {}: {}",
                    request.personName(),
                    e.getMessage());
        }

        try {
            if (updatedProduct.getStockQuantity() == 0) {
                productSearchService.searchByName(updatedProduct.getName())
                        .stream()
                        .findFirst()
                        .ifPresent(searchProduct -> {
                            searchProduct.setInStock(false);
                            productSearchService.saveProduct(searchProduct);
                        });
            }
        } catch (Exception e) {
            log.warn("Failed to update ES inStock for product {}: {}",
                    updatedProduct.getId(),
                    e.getMessage());
        }

        try {
            redisTemplate.delete("dashboard:" + request.personName());
        } catch (Exception e) {
            log.warn("Failed to evict dashboard cache for {}: {}",
                    request.personName(),
                    e.getMessage());
        }

        return savedReceipt;
    }
}