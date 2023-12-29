package com.personalHomepage.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalHomepage.demo.dto.ReceiptDTO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OpenAIAPI {
    public void detectResult(String ocr_result, ReceiptDTO receiptDTO) throws IOException {
        //gpt api 실행

        String url = "https://api.openai.com/v1/chat/completions";

        String questionFormat = "위 영수증에서 가맹점명이랑 지출금액('원'있으면 제외하고 숫자만 알려줘)이랑 결제일자(YYYY-MM-DD)랑 지출카테고리를 식비 또는 쇼핑 또는 도서 또는 기타 중 하나로 선택해 출력해줘";
        String replace_ocr_result = ocr_result.replace("\n", " ");
        String question = " " + replace_ocr_result + " " + questionFormat;
//        System.out.println("question: " + question);
        String jsonBody = "{\"model\": \"gpt-3.5-turbo\", \"messages\": [{\"role\": \"user\", \"content\": " + "\"" + question + "\"" + "}]}";
        // 개행 삭제
//        System.out.println("jsonBody" + jsonBody);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + openaiKey);
        connection.setDoOutput(true);

        // Write the request body
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Read the response
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            // Handle the response
            String jsonResponse = response.toString();

            //DTO에 json 데이터 추가
            receiptDTO.setJson_data(jsonResponse);

            try {
                // JSON 문자열을 JsonNode로 파싱
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(jsonResponse);

                // JsonNode에서 필요한 데이터 추출
                JsonNode choicesArray = jsonNode.get("choices");
                JsonNode firstChoice = choicesArray.get(0);
                JsonNode messageNode = firstChoice.get("message");
                String contentValue = messageNode.get("content").asText();

                //receiptDTO에 팀아이디, 지출금액, 결제일자, 지출카테고리 set

                List<String> valuesList = new ArrayList<>();
                String[] lines = contentValue.split("\n");
                for (String line: lines) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        String value = parts[1].trim();
                        valuesList.add(value);
                    }
                }
                System.out.println("contentValue: " + contentValue);

                //0: 가맹점, 1: 지출금액, 2: 결제일자, 3: 카테고리
                //가맹점
                receiptDTO.setVendor(valuesList.get(0));

                //지출금액
                Long amountData = Long.parseLong(valuesList.get(1).replace(",", "").replace("원", ""));
                receiptDTO.setAmount(amountData);

                //결제일자
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date dateData = dateFormat.parse(valuesList.get(2));
                receiptDTO.setPurchasedAt(dateData);

                //식비
                receiptDTO.setCategory(valuesList.get(3));

            } catch (Exception e) {
                e.printStackTrace();
            }
//            System.out.println("receiptDTO" + receiptDTO);
            // 여기서 jpa 사용해서 데이터 저장
            receiptService.saveReceipt(receiptDTO);
//            System.out.println(jsonResponse);

        }  finally {
            connection.disconnect();
        }

    }
}
