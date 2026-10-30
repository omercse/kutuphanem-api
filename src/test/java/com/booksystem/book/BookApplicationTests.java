package com.booksystem.book;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Ayakta bir Postgres istiyor, CI'da calismaz. Testcontainers gelince (Adim 6) acilacak.
@Disabled("Postgres gerektiriyor; Testcontainers ile yeniden etkinlestirilecek")
@SpringBootTest
class BookApplicationTests {

	@Test
	void contextLoads() {
	}

}
