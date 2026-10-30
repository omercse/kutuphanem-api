package com.booksystem.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * Kitabin okunma durumu.
 *
 * Daha once bu alan String idi ve gecerli degerler servis katmaninda bir
 * listeyle kontrol ediliyordu. Enum'a cevirince gecersiz bir degerin
 * veritabanina ulasmasi derleme asamasinda imkansiz hale geliyor.
 */
public enum BookStatus {

    OKUNMADI,
    OKUNUYOR,
    OKUNDU;

    private static final Locale TR = new Locale("tr", "TR");

    /**
     * Disaridan gelen metni enum'a cevirir. Turkce buyuk harf donusumu
     * onemli: "okunmadi" metnindeki 'i' harfi varsayilan Locale ile 'I'
     * olur ve eslesme basarisiz olur.
     *
     * @throws IllegalArgumentException deger listede yoksa
     */
    public static BookStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Hata: Durum bilgisi boş olamaz!");
        }

        String normalized = value.trim().toUpperCase(TR);

        return Arrays.stream(values())
                .filter(s -> s.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hata: Geçersiz durum bilgisi! Kabul edilen değerler: OKUNMADI, OKUNUYOR, OKUNDU"));
    }
}