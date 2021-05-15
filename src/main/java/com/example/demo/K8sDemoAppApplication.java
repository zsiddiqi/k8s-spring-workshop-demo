package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

@RestController
@SpringBootApplication
public class K8sDemoAppApplication {
private RestTemplate rest = new RestTemplateBuilder().build();

	public static void main(String[] args) {
		SpringApplication.run(K8sDemoAppApplication.class, args);
	}

@GetMapping("/")
public String hello() {
String name = rest.getForObject("http://k8s-workshop-name-service", String.class);
return "Hola " + name;
}
}
