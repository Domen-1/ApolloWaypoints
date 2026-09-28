package com.domen.apollowaypoints.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ValidationTest {
	@Test
	void names() {
		assertNull(Validation.checkName("Железная ферма 2"));
		assertNotNull(Validation.checkName(" "));
		assertNotNull(Validation.checkName("#12"));
		assertNotNull(Validation.checkName("12"), "digits only would clash with waypoint numbers");
		assertNull(Validation.checkName("Портал:хаб"), "Xaero allows colons; both of its formats escape them");
		assertNotNull(Validation.checkName("§cred"));
		assertNotNull(Validation.checkName("x".repeat(Validation.MAX_NAME + 1)));
	}

	@Test
	void symbols() {
		assertNull(Validation.checkSymbol("ЖФ"));
		assertNull(Validation.checkSymbol(""));
		assertNotNull(Validation.checkSymbol("ABC"));
		assertEquals("Ж", Validation.defaultSymbol("железная ферма"));
	}

	@Test
	void colors() {
		assertEquals(6, Colors.parse("gold"));
		assertEquals(6, Colors.parse("золотой"));
		assertEquals(20, Colors.parse("20"));
		assertEquals(-1, Colors.parse("21"));
		assertEquals(-1, Colors.parse("rainbow"));
		assertEquals(Colors.COUNT, Colors.ids().length);
	}
}
