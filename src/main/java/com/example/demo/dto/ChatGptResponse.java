package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ChatGptResponse {

	List<Choice> choices;

	@NoArgsConstructor
	@AllArgsConstructor
	@Data
	public static class Choice {
		int index;
		Message message;
	}
}
