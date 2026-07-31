package com.whatsupmarketplacebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class WhatsupMarketplaceBackendApplication {

	public static void main(String[] args) {

		SpringApplication.run(WhatsupMarketplaceBackendApplication.class, args);

		System.out.println("Marketing Agency Backend Application started");
		System.out.println("\n\n");
		System.err.println("  *****    *******  *******       *****   *******    *****    ******   *******");
		System.err.println(" *     *   *      *    *         *           *      *     *   *     *     *   ");
		System.err.println("*       *  *      *    *         *           *     *       *  *     *     *   ");
		System.err.println("*       *  *******     *          *****      *     *       *  ******      *   ");
		System.err.println("*********  *           *               *     *     *********  *   *       *   ");
		System.err.println("*       *  *           *               *     *     *       *  *    *      *   ");
		System.err.println("*       *  *        *******       *****      *     *       *  *     *     *   ");

	}

}
