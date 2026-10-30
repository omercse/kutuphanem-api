package com.booksystem.service.impl;

import com.booksystem.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode; // DOĞRUSU
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

@Service
@Slf4j
public class GeminiAiServiceImpl implements AiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Locale trLocale = Locale.forLanguageTag("tr-TR");

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.base-url}")
    private String baseUrl;

    @Value("${gemini.model}")
    private String model;

    @Override
    public List<String> detectCategoriesInBatch(List<String> bookTitlesWithAuthors) {
        List<String> defaultCategories = new ArrayList<>(Collections.nCopies(bookTitlesWithAuthors.size(), "GENEL"));

        if (apiKey == null || apiKey.trim().isEmpty() || bookTitlesWithAuthors.isEmpty()) {
            return defaultCategories;
        }

        try {
            String url = String.format("%s/%s:generateContent", baseUrl, model);

            StringBuilder bookListBuilder = new StringBuilder();
            for (int i = 0; i < bookTitlesWithAuthors.size(); i++) {
                bookListBuilder.append(i + 1).append(". ").append(bookTitlesWithAuthors.get(i)).append("\n");
            }

            String promptMessage = String.format(
                    "Sen profesyonel bir kütüphane veri sınıflandırma uzmanısın. Aşağıda numaralandırılmış kitapların türlerini analiz et:\n\n" +
                            "KULLANABİLECEĞİN KATEGORİLER (Sadece Bunlar):\n" +
                            "[\"EDEBİYAT / ROMAN\", \"TARİH\", \"DİN / İSLAM\", \"AİLE / PEDAGOJİ\", \"KİŞİSEL GELİŞİM / SAĞLIK\", \"GENEL\"]\n\n" +
                            "KESİN KURALLAR:\n" +
                            "1. Hikaye, şiir, kurgu, destan veya roman türündeki tüm eserlere \"EDEBİYAT / ROMAN\" de.\n" +
                            "2. Tarihi olaylar, siyasi tarih, biyografi veya imparatorluklarla ilgiliyse \"TARİH\" de.\n" +
                            "3. Tasavvuf, fıkıh, tefsir, hadis veya inanç üzerineyse \"DİN / İSLAM\" de.\n" +
                            "4. Çocuk yetiştirme, psikoloji, anne-baba ilişkileri ise \"AİLE / PEDAGOJİ\" de.\n" +
                            "5. Motivasyon, başarı, diyet, sağlıklı yaşam veya iletişim ise \"KİŞİSEL GELİŞİM / SAĞLIK\" de.\n" +
                            "6. DİKKAT: \"GENEL\" kategorisini SADECE kitap yukarıdaki 5 kategoriye kesinlikle uymuyorsa kullan! Olabildiğince \"GENEL\" cevabını vermekten KAÇIN.\n\n" +
                            "Kitap Listesi:\n%s\n\n" +
                            "Çıktı SADECE geçerli bir JSON array olmalıdır. Başka hiçbir kelime, açıklama veya kod bloğu işareti (```json gibi) yazma.\n" +
                            "Örnek Yanıt: [\"TARİH\", \"EDEBİYAT / ROMAN\", \"GENEL\"]",
                    bookListBuilder.toString()
            );

            Map<String, Object> textPart = Map.of("text", promptMessage);
            Map<String, Object> parts = Map.of("parts", List.of(textPart));
            Map<String, Object> contents = Map.of("contents", List.of(parts));

            // 🔥 ÇÖZÜM: API Key'i güvenli bir şekilde HTTP Header (Başlık) içine koyuyoruz
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("x-goog-api-key", apiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            org.springframework.http.HttpEntity<Map<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(contents, headers);

            log.info("Gemini API'sine {} adet kitap için TOPLU istek gönderiliyor...", bookTitlesWithAuthors.size());
            // postForObject metoduna contents yerine requestEntity veriyoruz
            String rawJson = restTemplate.postForObject(url, requestEntity, String.class);

            // ... (Geri kalan JSON parse işlemleri aynı)

            JsonNode response = objectMapper.readTree(rawJson);

            if (response != null && response.has("candidates")) {
                String aiResponse = response.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();

                // 🔥 1. DEĞİŞİKLİK: Yapay zekanın koyduğu ```json gibi Markdown işaretlerini temizliyoruz
                String cleanedAiResponse = aiResponse
                        .replaceAll("(?i)```json", "")
                        .replaceAll("```", "")
                        .trim();

                log.info("Yapay Zekadan dönen temizlenmiş JSON: {}", cleanedAiResponse);

                // 🚀 YENİ EKLENEN KURŞUN GEÇİRMEZ JSON PARSER
                JsonNode categoriesArray = objectMapper.readTree(cleanedAiResponse);
                List<String> finalizedCategories = new ArrayList<>();

                if (categoriesArray.isArray()) {
                    for (JsonNode item : categoriesArray) {
                        if (item.isTextual()) {
                            // SENARYO 1: AI uslu durup düz liste ["DİN", "TARİH"] döndüyse
                            finalizedCategories.add(item.asText().trim().toUpperCase(trLocale));
                        } else if (item.isObject() && item.has("kategori")) {
                            // SENARYO 2: AI kendi kafasına göre [{"kategori": "DİN"}] döndüyse
                            finalizedCategories.add(item.get("kategori").asText().trim().toUpperCase(trLocale));
                        } else {
                            // Beklenmedik bir yapı gelirse çökmek yerine güvenli limana geç
                            finalizedCategories.add("GENEL");
                        }
                    }
                }

                // Emniyet Kemeri: Eğer AI eksik/fazla cevap döndüyse uyar
                if (finalizedCategories.size() != bookTitlesWithAuthors.size()) {
                    log.warn("DİKKAT! Gönderilen ({}) ile AI'dan dönen ({}) kategori sayısı eşleşmiyor!",
                            bookTitlesWithAuthors.size(), finalizedCategories.size());
                }

                log.info("Gemini API toplu sınıflandırmayı başarıyla tamamladı.");
                return finalizedCategories;
            }

            return defaultCategories;

        } catch (Exception e) {
            log.error("Toplu Gemini API çağrısında hata oluştu: ", e);
            return defaultCategories;
        }
    }

}