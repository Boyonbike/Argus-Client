package dev.argus.client.utils.network;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public final class HttpRequests {
   private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30L);
   private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();

   private HttpRequests() {
   }

   public static HttpResponse<String> send(HttpRequest request) {
      return send(request, BodyHandlers.ofString());
   }

   public static <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> bodyHandler) {
      try {
         return CLIENT.send(request, bodyHandler);
      } catch (InterruptedException interrupted) {
         Thread.currentThread().interrupt();
         throw new CompletionException("HTTP request interrupted", interrupted);
      } catch (IOException failure) {
         throw new UncheckedIOException("HTTP request failed", failure);
      }
   }

   public static CompletableFuture<HttpResponse<String>> sendAsync(HttpRequest request) {
      return sendAsync(request, BodyHandlers.ofString());
   }

   public static <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> bodyHandler) {
      return CLIENT.sendAsync(request, bodyHandler).orTimeout(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
   }

   public static Builder get(String url) {
      return newRequest(url).GET();
   }

   public static Builder post(String url, BodyPublisher bodyPublisher) {
      return newRequest(url).POST(bodyPublisher);
   }

   public static Builder newRequest(String url) {
      return HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT);
   }
}
