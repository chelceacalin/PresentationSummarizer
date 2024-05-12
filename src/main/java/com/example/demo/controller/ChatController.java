package com.example.demo.controller;

import com.example.demo.dto.ChatGptRequest;
import com.example.demo.dto.ChatGptResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.IntStream;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

	final RestTemplate restTemplate;

	@Value("${openai.model}")
	private String model;

	@Value("${openai.api.url}")
	private String apiUrl;

	public final int TOKEN_SIZE = 4000;
	public final int TIMEOUT_ACCEPTED = 7000;

	@GetMapping("/chat")
	public Object chat(@RequestBody Input input) {
		if (input.getPrompt() == null || input.getPrompt().isEmpty()) {
			return "INVALID INPUT";
		}
		input.setPrompt(input.getPrompt().replaceAll("\"", ""));
		int chunks = (int) Math.ceil((double) input.getPrompt().length() / TOKEN_SIZE);
		System.out.println("Token Size: " + TOKEN_SIZE + " Chunks: " + chunks);
		System.out.println(input.getPrompt());
		System.out.println("Prompt length: " + input.getPrompt().length());


		List<String> textParts = splitText(input.getPrompt(), chunks);

		String summariesForList = generateSummariesForList(textParts, chunks);
		System.out.println(summariesForList);
		System.out.println("Summary lengths: " + summariesForList.length());

		System.out.println("\n---------------------------\n\n");

		return getChatGptTitleAndDescriptionForSummaries(summariesForList)
				.orElse(new SummaryResponse("Title Not Found", "Desc Not Found"));
	}

	public String generateSummariesForList(List<String> textParts, int totalChunks) {
		List<CompletableFuture<String>> futureList = new LinkedList<>();
		long startTime;
		try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
			startTime = System.currentTimeMillis();
			textParts.forEach(textPart ->
			{
				CompletableFuture<String> future =
						CompletableFuture
								.supplyAsync(() -> getSummaryForInput(new Input().setPrompt(textPart), TOKEN_SIZE / totalChunks), executorService)
								.exceptionally((throwable -> ""));
				futureList.add(future);
			});
		}

		List<String> resultList = new LinkedList<>();
		futureList.forEach(future -> {
			try {
				String summary = future.get(TIMEOUT_ACCEPTED, TimeUnit.SECONDS);
				System.out.println("Summary: " + summary);
				resultList.add(summary);
			} catch (InterruptedException | ExecutionException e) {
				log.error(e.getMessage());
			} catch (TimeoutException e) {
				resultList.add("");
			}
		});

		long endTime = System.currentTimeMillis();
		log.info("Execution Time -{}", endTime - startTime);
		return String.join(" ", resultList);
	}

	private List<String> splitText(String prompt, int chunks) {
		List<String> parts = new LinkedList<>();
		IntStream.rangeClosed(0, chunks - 1)
				.forEach(i -> {
					int start = i * TOKEN_SIZE;
					int end = start + TOKEN_SIZE;
					String textPart = prompt.substring(start, Math.min(prompt.length(), end));
					parts.add(textPart);
				});
		return parts;
	}


	public Optional<SummaryResponse> getChatGptTitleAndDescriptionForSummaries(String summaries) {
		ChatGptRequest chatGptRequest = getChatGptRequestPrompt(summaries);
		ChatGptResponse response = restTemplate.postForObject(apiUrl, chatGptRequest, ChatGptResponse.class);
		if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
			return Optional.empty();
		}

		String content = response.getChoices().getFirst().getMessage().getContent();
		content = content.replace("```json", "").replace("```", "").trim();
		SummaryResponse summaryResponse;
		try {
			summaryResponse = new ObjectMapper().readValue(content, SummaryResponse.class);
		} catch (Exception e) {
			log.error("Error parsing JSON content: {}", e.getMessage());
			return Optional.of(new SummaryResponse("Error", "Failed to parse response"));
		}

		return Optional.of(summaryResponse);
	}

	private ChatGptRequest getChatGptRequestPrompt(String summaries) {
		var prompt = """
				I would like you to generate a neutral and concise title
				\tand a long detailed description, but the description should not be longer than 500 characters for a provided video transcript.
				\tThe title and description should be derived solely from the content of the transcript
				\tand should be in the same language as the transcript.
				\tI also request that the response be formatted in JSON without using markdown language.
				\tThis is a sample response { title: 'identified title' , description: 'identified description' }
				\tI want the title and and description to be in the language of the extracted words


				\tDo not use any kind of markdown language. Please give me only the json text in json format.


				\tThe video transcript is:

				%s""".formatted(summaries);

		return new ChatGptRequest(model, prompt);
	}


	private String getSummaryForInput(Input input, int characters) {
		String supplyPrompt = """
											I want you to generate a summary for the following prompt.
											Make sure that the summary is in the same language as the prompt. At the same time, create the summary thinking of the whole concept, the main idea, since it's a presentation of a concept, 
											I want the summary to reflect that concept that was presented.
											""" + input.getPrompt() + ". Please make sure that the summary is no longer than " + characters + " characters!";

		ChatGptRequest chatGptRequest = new ChatGptRequest(model, supplyPrompt);

		ChatGptResponse response = restTemplate.postForObject(apiUrl, chatGptRequest, ChatGptResponse.class);

		if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
			return "No response";
		}
		return response.getChoices().getFirst().getMessage().getContent();
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class SummaryResponse {
		String title;
		String description;
	}


	@Data
	public static class Input {
		String prompt;
	}
}



