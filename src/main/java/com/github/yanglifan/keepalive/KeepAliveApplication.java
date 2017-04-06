package com.github.yanglifan.keepalive;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.hystrix.dashboard.EnableHystrixDashboard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@EnableHystrixDashboard
@EnableCircuitBreaker
@SpringBootApplication
public class KeepAliveApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeepAliveApplication.class);

    private static final int CORE_POOL_SIZE = 100;
    private static final int CONN_POOL_SIZE = 100;
    private static final int DELAY_IN_SECONDS = 1000;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(CORE_POOL_SIZE);

    // Not necessary
    private ConnectionPool okHttpConnectionPool = new ConnectionPool(5, 2, TimeUnit.MINUTES);

    @Autowired(required = false)
    private Client client;

    public static void main(String[] args) {
        SpringApplication.run(KeepAliveApplication.class, args);
    }

    @Profile("client")
    @Bean
    public CommandLineRunner clientRunner() {
        LOGGER.info("Client runner startup");
        return (args) -> {
            for (int i = 0; i < CORE_POOL_SIZE; i++) {
                Random random = new Random();
                scheduler.scheduleAtFixedRate(() -> {

                    try {
                        client.call();
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }, random.nextInt(DELAY_IN_SECONDS), DELAY_IN_SECONDS, TimeUnit.MILLISECONDS);
            }
        };
    }

    @Profile("okHttp")
    @Bean
    public ClientHttpRequestFactory okHttpClientFactory() {
        LOGGER.info("Start to build OkHttp ClientHttpRequestFactory");
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectionPool(okHttpConnectionPool);
        OkHttpClient okHttpClient = builder.build();
        return new OkHttp3ClientHttpRequestFactory(okHttpClient);
    }

    @Profile("apache")
    @Bean
    public ClientHttpRequestFactory apacheHttpClientFactory() {
        return new CustomHttpComponentsClientHttpRequestFactory();
    }

    @Profile("client")
    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory clientHttpRequestFactory) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(clientHttpRequestFactory);
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
            connectionManager.setMaxTotal(CONN_POOL_SIZE * 2);
            connectionManager.setDefaultMaxPerRoute(CONN_POOL_SIZE);

            HttpClientBuilder builder = HttpClients.custom()
                    .setConnectionManager(connectionManager);

            return builder.build();
        }
    }

    @Profile("server")
    @RequestMapping("/keep-alive/hello")
    @RestController
    public static class HelloController {
        @Value("${sleepTime:100}")
        private int sleepTime;

        @PostConstruct
        public void init() {
            LOGGER.info("Sleep time is {}", sleepTime);
        }

        @RequestMapping(method = RequestMethod.GET)
        public String hello() throws Exception {
            Thread.sleep(sleepTime);
            return "world";
        }
    }
}
