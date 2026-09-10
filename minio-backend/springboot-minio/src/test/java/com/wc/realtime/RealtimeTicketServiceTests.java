package com.wc.realtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.mock.http.server.reactive.*;
import org.springframework.http.server.*;
import org.springframework.http.*;
class RealtimeTicketServiceTests {
 @Test void ticketIsSingleUseAndUnpredictable() {
  var service = new RealtimeTicketService();
  var ticket = service.issue(7);
  assertEquals(43,ticket.ticket().length());
  assertEquals(7,service.consume(ticket.ticket()));
  assertNull(service.consume(ticket.ticket()));
  assertNull(service.consume("forged"));
 }
 @Test void expiredTicketIsRejectedAtBoundary() {
  Clock clock = mock(Clock.class);
  Instant start = Instant.parse("2026-09-10T00:00:00Z");
  when(clock.instant()).thenReturn(start);
  var service = new RealtimeTicketService(clock);
  var ticket = service.issue(1);
  when(clock.instant()).thenReturn(start.plusSeconds(60));
  assertNull(service.consume(ticket.ticket()));
 }
 @Test void concurrentReplayHasExactlyOneWinner() throws Exception {
  var service = new RealtimeTicketService();
  var ticket = service.issue(4);
  var pool = Executors.newFixedThreadPool(8);
  try {
   List<Callable<Integer>> calls = new ArrayList<>();
   for(int i=0;i<20;i++) calls.add(() -> service.consume(ticket.ticket()));
   int winners=0;
   for(var f: pool.invokeAll(calls)) if(f.get()!=null) winners++;
   assertEquals(1,winners);
  } finally { pool.shutdownNow(); }
 }
 @Test void perUserLimitDoesNotBlockAnotherUser() {
  var service = new RealtimeTicketService();
  for(int i=0;i<5;i++) service.issue(1);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.issue(1));
  assertNotNull(service.issue(2));
 }
 @Test void disallowedOriginDoesNotConsumeTicketAndReplayFails() {
  var service = new RealtimeTicketService();
  var guard = new TicketHandshakeInterceptor(service,"http://localhost:8081");
  var ticket = service.issue(9);
  var request = mock(ServerHttpRequest.class);
  var response = mock(ServerHttpResponse.class);
  var headers = new HttpHeaders();
  headers.setOrigin("https://evil.example");
  when(request.getHeaders()).thenReturn(headers);
  when(request.getURI()).thenReturn(java.net.URI.create("http://localhost/ws/funasr?ticket="+ticket.ticket()));
  assertFalse(guard.beforeHandshake(request,response,null,new HashMap<>()));
  verify(response).setStatusCode(HttpStatus.FORBIDDEN);
  headers.setOrigin("http://localhost:8081");
  var attrs = new HashMap<String,Object>();
  assertTrue(guard.beforeHandshake(request,response,null,attrs));
  assertEquals(9,attrs.get("authenticatedUserId"));
  assertFalse(guard.beforeHandshake(request,response,null,new HashMap<>()));
  verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
 }
}
