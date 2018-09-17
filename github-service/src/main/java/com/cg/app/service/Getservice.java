package com.cg.app.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tomcat.util.codec.binary.Base64;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Service
@RefreshScope
public class Getservice {

	public static final String BLANK = "";

	@Value("${git.token}")
	String TOKEN;

	@Value("${git.userName}")
	String USER;

	@Value("${git.repository}")
	String REPONAME;

	@Value("${git.branch}")
	String BRANCH;

	@Value("${git.commitMsg}")
	String COMMIT_MESSAGE;

	@Value("${git.field}")
	String FIELD;

	String FIELD_UPDATE_VALUE;

	@Value("${git.resturl}")
	String resturl;

	@Autowired
	GitCommitService gitCommitService;

	public String get(String FILENAME)

	{
		String responseMessage = BLANK;
		String updatedContent = BLANK;
		String sb = BLANK;
		GitHubClient githubClient = new GitHubClient();
		githubClient.setOAuth2Token(TOKEN);
		FIELD_UPDATE_VALUE = Getservice.quarter();
		RepositoryService repoService = new RepositoryService(githubClient);

		Repository repo;
		try {
			repo = repoService.getRepository(USER, REPONAME);
		} catch (IOException e) {
			throw new RuntimeException(
					"Error in connecting to Reporitory, please check the CONNECTION or TOKEN or try with proper USER NAME and REPOSITORY NAME");
		}

		ContentsService contentService = new ContentsService(githubClient);

		List<RepositoryContents> contents;
		try {
			contents = contentService.getContents(repo, FILENAME);
			for (RepositoryContents content : contents) {
				String fileConent = content.getContent();
				String valueDecoded = new String(Base64.decodeBase64(fileConent.getBytes()));
				sb = sb + valueDecoded;
			}
		} catch (IOException e) {
			throw new RuntimeException(
					"Entered file name cannot be found in the repository, please give proper filename");
		}

		/*
		 * String prettyJson=toPrettyFormat(sb); updatedContent = prettyJson;
		 * responseMessage = gitCommitService.doInBackground(githubClient, repo, BRANCH,
		 * REPONAME, FILENAME, updatedContent, FILENAME + " " + COMMIT_MESSAGE, USER);
		 */
		try {
			JsonArray jsonArray = (JsonArray) new JsonParser().parse(sb);
			Map<String, Object> fieldsMap = null;
			JsonObject explrObject = null;
			List<JsonObject> list = new ArrayList<>();
			Boolean flag = validateField(jsonArray.get(0).getAsJsonObject(), FIELD);
			if (flag) {
				for (int i = 0; i < jsonArray.size(); i++) {
					fieldsMap = new HashMap<>();
					explrObject = jsonArray.get(i).getAsJsonObject();

					String key = FIELD;
					String new_value = FIELD_UPDATE_VALUE;
					explrObject.addProperty(key, new_value);
					list.add(explrObject);
				}
			} else
				throw new RuntimeException();
			String compactJson = list.toString();
			String prettyJson = toPrettyFormat(compactJson);
			updatedContent = prettyJson;
			responseMessage = gitCommitService.doInBackground(githubClient, repo, BRANCH, REPONAME, FILENAME,
					updatedContent, FILENAME + " " + COMMIT_MESSAGE, USER);

		} catch (Exception e) {
			throw new RuntimeException("Invalid Field, Field that you have entered is not present in the file");

		}

		RestTemplate restTemplate = new RestTemplate();
		String result = restTemplate.postForObject(resturl, updatedContent, String.class);

		gitCommitService.sendMail();

		if (!responseMessage.equals("") || responseMessage != null) {
			return responseMessage;
		}
		return responseMessage;

	}

	public static Boolean validateField(JsonObject jsonObj, String field) {

		if (jsonObj.has(field)) {
			return true;
		} else
			return false;

	}

	public static String toPrettyFormat(String jsonString) {
		String gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create()
				.toJson(new JsonParser().parse(jsonString));
		return gson;
	}

	public static String quarter() {
		String next = BLANK;

		int quarter = LocalDate.now().get(IsoFields.QUARTER_OF_YEAR);
		String year = Year.now().format(DateTimeFormatter.ofPattern("yy"));

		if (quarter < 4) {
			int qut = quarter + 1;
			next = qut + "Q" + year;
		} else if (quarter == 4) {
			int qut = 1;
			next = qut + "Q" + year;
		}
		return next;
	}

}
