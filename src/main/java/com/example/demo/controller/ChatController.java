package com.example.demo.controller;

import com.example.demo.dto.Input;
import com.example.demo.dto.SummaryResponse;
import com.example.demo.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

		return chatService.getChatGptTitleAndDescriptionForSummaries(summariesForList)
				.orElse(new SummaryResponse("Title Not Found", "Desc Not Found"));
	}


}



