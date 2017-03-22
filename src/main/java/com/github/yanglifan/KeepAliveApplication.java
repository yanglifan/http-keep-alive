package com.github.yanglifan;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@EnableScheduling
@SpringBootApplication
public class KeepAliveApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeepAliveApplication.class);

    private static final int DEFAULT_MAX_PER_ROUTE = 10;

    private static final String TEST_SERVICE_URL = "http://keepAliveService/keep-alive/hello";

    private RestTemplate restTemplate = buildRestTemplate();

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(100);

    public static void main(String[] args) {
        SpringApplication.run(KeepAliveApplication.class, args);
    }

    @Profile("client")
    @Bean
    public CommandLineRunner clientRunner() {
        LOGGER.info("Client runner startup");
        return new CommandLineRunner() {
            @Override
            public void run(String... strings) throws Exception {
                scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        String response = restTemplate.getForObject(TEST_SERVICE_URL, String.class);

                        Random random = new Random();
                        if (random.nextInt(100) % 99 == 0) {
                            System.out.println(response);
                        }
                    }
                }, 0, 10, TimeUnit.MILLISECONDS);
            }
        };
    }

    private RestTemplate buildRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new CustomHttpComponentsClientHttpRequestFactory());
        return restTemplate;
    }

    static class CustomHttpComponentsClientHttpRequestFactory extends
            HttpComponentsClientHttpRequestFactory {
        CustomHttpComponentsClientHttpRequestFactory() {
            setHttpClient(buildHttpClient());
        }

        private HttpClient buildHttpClient() {
            PoolingHttpClientConnectionManager connectionManager =
                    new PoolingHttpClientConnectionManager(2, TimeUnit.MINUTES);
            connectionManager.setMaxTotal(DEFAULT_MAX_PER_ROUTE * 2);
            connectionManager.setDefaultMaxPerRoute(DEFAULT_MAX_PER_ROUTE);

            HttpClientBuilder builder = HttpClients.custom()
                    .setConnectionManager(connectionManager);

            return builder.build();
        }
    }

    @Profile("server")
    @RequestMapping("/keep-alive/hello")
    @RestController
    public static class HelloController {
        @RequestMapping(method = RequestMethod.GET)
        public String hello() throws Exception {
            Thread.sleep(10);
            return "world";
        }
    }
}
