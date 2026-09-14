package com.wc.system;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import javax.sql.DataSource;
import java.sql.Connection;
import com.wc.config.MinioInfo;
import com.wc.funasr.config.FunasrProperties;
import com.wc.tts.config.TtsProperties;
import com.wc.voiceprint.config.VoiceprintProperties;
class SystemStatusControllerTests {
 @Test void stoppedDependenciesProduceSanitizedCachedSnapshot() throws Exception {
  var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/", exchange -> { exchange.sendResponseHeaders(503,-1); exchange.close(); });
  server.start();
  String url="http://127.0.0.1:"+server.getAddress().getPort();
  var ds=mock(DataSource.class); var connection=mock(Connection.class);
  when(ds.getConnection()).thenReturn(connection); when(connection.isValid(1)).thenReturn(true);
  var minio=new MinioInfo(); minio.setEndpoint(url);
  var asr=new FunasrProperties(); asr.setHttpBaseUrl(url); asr.setWsUrl(url.replace("http:","ws:"));
  var tts=new TtsProperties(); tts.setHttpBaseUrl(url);
  var sv=new VoiceprintProperties(); sv.setHttpBaseUrl(url);
  var controller=new SystemStatusController(ds,minio,asr,tts,sv);
  try {
   var response=controller.status(); var snapshot=response.getBody();
   assertEquals("DEGRADED",snapshot.status());
   assertEquals(6,snapshot.services().size());
   assertEquals("UP",snapshot.services().get(0).status());
   assertTrue(snapshot.services().stream().skip(1).allMatch(s -> s.status().equals("DOWN")));
   assertTrue(snapshot.services().stream().noneMatch(s -> s.message().contains("127.0.0.1")));
   assertSame(snapshot,controller.status().getBody());
   verify(ds,times(1)).getConnection();
  } finally { controller.close(); server.stop(0); }
 }
}
