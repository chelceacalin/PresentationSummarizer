package com.example.demo.service;

import com.example.demo.dto.ChatGptRequest;
import com.example.demo.dto.ChatGptResponse;
import com.example.demo.dto.Input;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

	final RestTemplate restTemplate;

	@Value("${openai.model}")
	String model;

	@Value("${openai.api.url}")
	String apiUrl;

	@Value("${openai.token.size}")
	int TOKEN_SIZE;

	final int TIMEOUT_ACCEPTED = 7000;

	public List<String> splitText(String prompt, int chunks) {
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
				log.info("Summary: {}", summary);
				resultList.add(summary);
			} catch (InterruptedException | ExecutionException e) {
				log.error(e.getMessage());
			} catch (TimeoutException e) {
				resultList.add("");
			}
		});

		long endTime = System.currentTimeMillis();
		log.info("Execution Time -{}", Math.abs(endTime - startTime));
		return String.join(" ", resultList);
	}

	private ChatGptRequest getChatGptRequestPrompt(String summaries) {
		int MAX_DESCRIPTION_LENGTH = 700;
		var prompt = """
				I would like you to generate a neutral and concise title
				\tand a long detailed description, but the description should not be longer than %s characters for a provided video transcript.
				\tThe title and description should be derived solely from the content of the transcript
				\tand should be in the same language as the transcript.
				\tI also request that the response be formatted in JSON without using markdown language.
				\tThis is a sample response { title: 'identified title' , description: 'identified description' }
				\tI want the title and and description to be in the language of the extracted words


				\tDo not use any kind of markdown language. Please give me only the json text in json format.


				\tThe video transcript is:

				%s""".formatted(MAX_DESCRIPTION_LENGTH, summaries);

		return new ChatGptRequest(model, prompt);
	}

	public ChatGptResponse getChatGptResponse(String summaries) {
		ChatGptRequest chatGptRequest = getChatGptRequestPrompt(summaries);
		return restTemplate.postForObject(apiUrl, chatGptRequest, ChatGptResponse.class);
	}

	public int calculateTotalChunks(String prompt) {
		int chunks = (int) Math.ceil((double) prompt.length() / TOKEN_SIZE);
		log.info("Token Size: {} Chunks: {}", TOKEN_SIZE, chunks);
		return chunks;
	}
}
