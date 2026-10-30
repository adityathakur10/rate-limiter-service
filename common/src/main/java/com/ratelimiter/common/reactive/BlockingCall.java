package com.ratelimiter.common.reactive;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

/**
 * Runs blocking code (JPA, JDBC) on the bounded-elastic pool so the event loop never waits on it.
 * Wrap a whole blocking service method, so @Transactional stays on one thread.
 */
public final class BlockingCall {

    private BlockingCall() {
    }

    public static <T> Mono<T> call(Callable<? extends T> callable) {
        return Mono.<T>fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    public static Mono<Void> run(Runnable runnable) {
        return Mono.fromRunnable(runnable).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
