package com.example.lab05.service;

import com.example.lab05.dto.DashboardResponse;
import com.example.lab05.model.cassandra.SensorReading;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.model.neo4j.Person;
import com.example.lab05.repository.mongo.PurchaseReceiptRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final SocialGraphService socialGraphService;
    private final SensorService sensorService;
    private final ProductSearchService productSearchService;
    private final RedisTemplate<String, Object> redisTemplate;

    public DashboardService(
            PurchaseReceiptRepository purchaseReceiptRepository,
            SocialGraphService socialGraphService,
            SensorService sensorService,
            ProductSearchService productSearchService,
            RedisTemplate<String, Object> redisTemplate
    ) {
        this.purchaseReceiptRepository = purchaseReceiptRepository;
        this.socialGraphService = socialGraphService;
        this.sensorService = sensorService;
        this.productSearchService = productSearchService;
        this.redisTemplate = redisTemplate;
    }

    public DashboardResponse getDashboard(String personName) {
        String cacheKey = "dashboard:" + personName;

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof DashboardResponse response) {
                return new DashboardResponse(
                        response.personName(),
                        response.totalSpent(),
                        response.purchaseCount(),
                        response.recentPurchases(),
                        response.friendRecommendations(),
                        response.friendsOfFriends(),
                        response.recentActivity(),
                        response.youMightAlsoLike(),
                        true
                );
            }
        } catch (Exception e) {
            log.warn("Redis cache check failed for {}: {}", personName, e.getMessage());
        }

        List<PurchaseReceipt> receipts = purchaseReceiptRepository.findByPersonName(personName);

        Double totalSpent = receipts.stream()
                .mapToDouble(PurchaseReceipt::getTotalPrice)
                .sum();

        Integer purchaseCount = receipts.size();

        List<PurchaseReceipt> recentPurchases = receipts.stream()
                .sorted(Comparator.comparing(PurchaseReceipt::getPurchasedAt).reversed())
                .limit(5)
                .collect(Collectors.toList());

        List<Map<String, Object>> friendRecommendations = new ArrayList<>();
        List<String> friendsOfFriends = new ArrayList<>();

        try {
            friendRecommendations = socialGraphService.getRecommendations(personName, 5);
            friendsOfFriends = socialGraphService.getFriendsOfFriends(personName).stream()
                    .map(Person::getName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to fetch Neo4j data for {}: {}", personName, e.getMessage());
        }

        List<SensorReading> recentActivity = new ArrayList<>();

        try {
            recentActivity = sensorService.getLatestReadings(
                    "user-activity-" + personName.toLowerCase(),
                    10
            );
        } catch (Exception e) {
            log.warn("Failed to fetch activity for {}: {}", personName, e.getMessage());
        }

        List<String> youMightAlsoLike = new ArrayList<>();

        try {
            Set<String> alreadyPurchased = receipts.stream()
                    .map(PurchaseReceipt::getProductName)
                    .collect(Collectors.toSet());

            Set<String> categories = receipts.stream()
                    .map(PurchaseReceipt::getProductCategory)
                    .collect(Collectors.toSet());

            for (String category : categories) {
                List<String> suggestions = productSearchService.getByCategory(category)
                        .stream()
                        .filter(product -> !alreadyPurchased.contains(product.getName()))
                        .map(product -> product.getName())
                        .limit(2)
                        .toList();

                youMightAlsoLike.addAll(suggestions);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch ES suggestions for {}: {}", personName, e.getMessage());
        }

        DashboardResponse response = new DashboardResponse(
                personName,
                totalSpent,
                purchaseCount,
                recentPurchases,
                friendRecommendations,
                friendsOfFriends,
                recentActivity,
                youMightAlsoLike,
                false
        );

        try {
            redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("Failed to cache dashboard for {}: {}", personName, e.getMessage());
        }

        return response;
    }
}