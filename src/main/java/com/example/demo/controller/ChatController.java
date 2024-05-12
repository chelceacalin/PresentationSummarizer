package com.example.demo.controller;

import com.example.demo.dto.ChatGptResponse;
import com.example.demo.dto.Input;
import com.example.demo.dto.SummaryResponse;
import com.example.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {
	final ChatService chatService;

	@Value("${openai.token.size}")
	public int TOKEN_SIZE;


	@GetMapping("/chat")
	public Object chat(@RequestBody Input input) {
		String prompt = input.getPrompt();
		if (prompt == null || prompt.isEmpty()) {
			return "INVALID INPUT";
		}
		input.setPrompt(prompt.replaceAll("\"", ""));
		int chunks = chatService.calculateTotalChunks(prompt);

		log.info("Prompt {}", prompt);
		log.info("Prompt length: {}", prompt.length());

		List<String> textParts = chatService.splitText(prompt, chunks);

		String summariesForList = chatService.generateSummariesForList(textParts, chunks);
		log.info("Summaries: {}", summariesForList);
		log.info("Summary lengths: {}", summariesForList.length());

		return getChatGptTitleAndDescriptionForSummaries(summariesForList)
				.orElse(new SummaryResponse("Title Not Found", "Desc Not Found"));
	}


	public Optional<SummaryResponse> getChatGptTitleAndDescriptionForSummaries(String summaries) {
		ChatGptResponse response = chatService.getChatGptResponse(summaries);

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


}



