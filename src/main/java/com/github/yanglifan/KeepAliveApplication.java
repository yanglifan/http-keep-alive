package com.github.yanglifan;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@EnableScheduling
@SpringBootApplication
public class KeepAliveApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeepAliveApplication.class);

    private static final int CORE_POOL_SIZE = 100;
    private static final int CONN_POOL_SIZE = 100;

    private static final String TEST_SERVICE_URL = "http://keepAliveService:8080/keep-alive/hello";

    @Autowired
    private RestTemplate restTemplate;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(CORE_POOL_SIZE);

    private AtomicInteger count = new AtomicInteger();

    // Not necessary
    private ConnectionPool okHttpConnectionPool = new ConnectionPool(5, 2, TimeUnit.MINUTES);

    public static void main(String[] args) {
        SpringApplication.run(KeepAliveApplication.class, args);
    }

    @Profile("client")
    @Bean
    public CommandLineRunner clientRunner() {
        LOGGER.info("Client runner startup");
        return (args) -> {
            for (int i = 0; i < CORE_POOL_SIZE; i++) {
                scheduler.scheduleAtFixedRate(() -> {
                    String response = null;
                    try {
                        response = restTemplate.getForObject(TEST_SERVICE_URL, String.class);
                        count.incrementAndGet();
                    } catch (RestClientException e) {
                        LOGGER.error(e.getMessage(), e);
                    }

                    if (count.get() % 100 == 0) {
                        LOGGER.info(response);
                    }
                }, 0, 100, TimeUnit.MILLISECONDS);
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
