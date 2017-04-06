package com.github.yanglifan.keepalive;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

@Profile("client")
@Component
public class Client {
    private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

    private static final String TEST_SERVICE_URL = "http://keepAliveService/keep-alive/hello";

    private final AtomicInteger count = new AtomicInteger();

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @HystrixCommand(
            commandProperties = {
                    @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "1000")
            },
            threadPoolProperties = {
                    @HystrixProperty(name = "coreSize", value = "200")
            }
    )
    public void call() {
        String response = null;
        try {
            response = restTemplate.getForObject(TEST_SERVICE_URL, String.class);
        } catch (RestClientException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (count.incrementAndGet() % 1000 == 0) {
            LOGGER.info(response);
        }
    }
}
