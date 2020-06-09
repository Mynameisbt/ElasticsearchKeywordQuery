package blog.brianthomas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.waiting.SuccessOrFailure;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.Assert.assertEquals;

public class TestKeywordQuery {

	private HttpClient httpClient;
	@ClassRule
	public static DockerComposeRule docker = DockerComposeRule.builder()
			.file("src/test/resources/docker-compose.yml")
			.waitingForHostNetworkedPort(9200, target -> {
				return SuccessOrFailure.success();
			})
			.build();


	@Before
	public void setup() throws IOException, InterruptedException {
		//Wait for elasticsearch to startup
		Thread.sleep(30000);
		httpClient = HttpClient.newHttpClient();
		HttpRequest request1 = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:9200/docs"))
				.PUT(HttpRequest.BodyPublishers.noBody())
				.build();
		HttpResponse<String> response = httpClient.send(request1, HttpResponse.BodyHandlers.ofString());
		HttpRequest request2 = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:9200/docs/1"))
				.POST(HttpRequest.BodyPublishers.ofString("{\"version\":\"1.0.xCodeword\"}"))
				.header("Content-Type", "application/json")
				.build();
		httpClient.send(request2, HttpResponse.BodyHandlers.ofString());
		//Wait for document to get indexed
		Thread.sleep(1000);
	}

	@Test
	public void test() throws IOException, InterruptedException {
		String searchQuery = "{" +
				"\"query\": {" +
					"\"term\": {"+
						"\"version\": {"+
							"\"value\": \"1.0.xCodeword\""+
						"}" +
					"}" +
				"}}";

		HttpRequest versionQuery = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:9200/docs/_search"))
				.POST(HttpRequest.BodyPublishers.ofString(searchQuery))
				.header("Content-Type", "application/json")
				.build();
		HttpResponse<String> response = httpClient.send(versionQuery, HttpResponse.BodyHandlers.ofString());
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode responseJson = mapper.readValue(response.body(), ObjectNode.class);
		assertEquals(0,responseJson.get("hits").get("total").get("value").asInt());

		String searchQueryKeyword = "{" +
				"\"query\": {" +
				"\"term\": {"+
				"\"version.keyword\": {"+
				"\"value\": \"1.0.xCodeword\""+
				"}" +
				"}" +
				"}}";
		versionQuery = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:9200/docs/_search"))
				.POST(HttpRequest.BodyPublishers.ofString(searchQueryKeyword))
				.header("Content-Type", "application/json")
				.build();
		response = httpClient.send(versionQuery, HttpResponse.BodyHandlers.ofString());
		responseJson = mapper.readValue(response.body(), ObjectNode.class);
		assertEquals(1,responseJson.get("hits").get("total").get("value").asInt());
	}

}
