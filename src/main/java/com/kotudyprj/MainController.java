package com.kotudyprj;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.Gson;
import com.kotudyprj.dao.IKakaoDao;
import com.kotudyprj.dao.IUserRankingDao;
import com.kotudyprj.dao.IVocabularyNoteDao;
import com.kotudyprj.dao.IWordRankingDao;
import com.kotudyprj.dao.IWordsDao;
import com.kotudyprj.dto.KakaoDto;
import com.kotudyprj.dto.QuizTemplateDto;
import com.kotudyprj.dto.VocabularyNoteDto;
import com.kotudyprj.dto.WordItemDto;
import com.kotudyprj.dto.WordSenseDto;
import com.kotudyprj.dto.WordsDto;
import com.kotudyprj.service.KakaoAPI;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class MainController {

	@Autowired
	IWordsDao iWordsDao;

	@Autowired
	IVocabularyNoteDao iVocabularyNoteDao;

	@Autowired
	IKakaoDao iKakaoDao;

	@Autowired
	KakaoAPI kakaoAPI;

	@Autowired
	IUserRankingDao iUserRankingDao;

	@Autowired
	IWordRankingDao iWordRankingDao;

	HttpSession loginId;

	@RequestMapping("/")
	public String root() throws Exception {

		return "";
	}

	static public class Morpheme {
		final String text;
		final String type;
		Integer count;

		public Morpheme(String text, String type, Integer count) {
			this.text = text;
			this.type = type;
			this.count = count;
		}
	}

	static public class NameEntity {
		final String text;
		final String type;
		Integer count;

		public NameEntity(String text, String type, Integer count) {
			this.text = text;
			this.type = type;
			this.count = count;
		}
	}

	@GetMapping("/kakaoAuth")
	public Object kakaoLogin(@RequestParam String code, HttpServletRequest req, KakaoDto kakaoDto) {

		// ?????????????????? ???????????? ????????? ??? ????????? ?????? ???????????? ?????? ??????
		HttpSession session = req.getSession(true);
		String access_Token = kakaoAPI.getAccessToken(code);
		HashMap<String, Object> userInfo = kakaoAPI.getUserInfo(access_Token);
		// System.out.println("login Controller : " + userInfo);

		if (userInfo.get("email") != null) {

			kakaoDto.setUserId(userInfo.get("email"));
			kakaoDto.setNickName(userInfo.get("nickname"));
			kakaoDto.setImage(userInfo.get("profile_image"));

			iKakaoDao.registerDao(kakaoDto.getUserId(), kakaoDto.getNickName(), kakaoDto.getImage());
			if (iUserRankingDao.checkRankingUserId(kakaoDto.getUserId()) == null) {
				iUserRankingDao.createRankingInfo(kakaoDto.getUserId(), kakaoDto.getNickName(), kakaoDto.getImage());
			}
			List check = iKakaoDao.loginDao(kakaoDto.getUserId());
			loginId = req.getSession();
			loginId.setAttribute("userId", kakaoDto.getUserId());

		}

		return userInfo;
	}

	@GetMapping("/getInfo")
	public Object getInfo() {

		Object a = loginId.getAttribute("userId");
		if (a == null) {

			org.apache.tomcat.jni.Error.osError();

		}

		return a;

	}

	@PostMapping("/kakaoLogout")
	public String logout() {

		loginId.removeAttribute("userId");
		return "index";
	}

	@GetMapping("/dailyWords")
	public List<WordsDto> dailyWords(WordsDto wordsDto) {
		List<WordsDto> list = new ArrayList<>();
		list = iWordsDao.selectWordsDao();
		return list;

	}

	@PostMapping("/searchWord")
	public List<String> paraphraseCheck2(@RequestBody Map<String, String> body) {

		List<String> finalDtoList = new ArrayList<>();
		// FinalDto finalDto = null;
		String openApiURL = "http://aiopen.etri.re.kr:8000/WiseNLU";
		String accessKey = "16738d75-2241-45a6-8c0d-0b06580f2a65"; // ???????????? API Key
		String analysisCode = "ner"; // ?????? ?????? ??????
		String text = ""; // ????????? ????????? ?????????
		Gson gson = new Gson();

		Map<String, Object> request = new HashMap<>();
		Map<String, String> argument = new HashMap<>();

		argument.put("analysis_code", body.get("analysisCode"));
		argument.put("text", body.get("text"));

		request.put("access_key", accessKey);
		request.put("argument", argument);
		System.out.println("argument:" + argument);

		URL url;
		Integer responseCode = null;
		String responBodyJson = null;
		Map<String, Object> responeBody = null;

		try {
			url = new URL(openApiURL);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);

			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.write(gson.toJson(request).getBytes("UTF-8"));
			wr.flush();
			wr.close();

			responseCode = con.getResponseCode();
			InputStream is = con.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			StringBuffer sb = new StringBuffer();

			String inputLine = "";
			while ((inputLine = br.readLine()) != null) {
				sb.append(inputLine);
			}
			responBodyJson = sb.toString();

			// http ?????? ?????? ??? ??????
			if (responseCode != 200) {
				// ?????? ?????? ??????
				System.out.println("[error] " + responBodyJson);

			}

			responeBody = gson.fromJson(responBodyJson, Map.class);
			Integer result = ((Double) responeBody.get("result")).intValue();
			Map<String, Object> returnObject;
			List<Map> sentences;

			// ?????? ?????? ?????? ??? ??????
			if (result != 0) {

				// ?????? ?????? ??????
				System.out.println("[error] " + responeBody.get("result"));

			}

			// ?????? ?????? ??????
			returnObject = (Map<String, Object>) responeBody.get("return_object");
			sentences = (List<Map>) returnObject.get("sentence");

			Map<String, Morpheme> morphemesMap = new HashMap<String, Morpheme>();
			Map<String, NameEntity> nameEntitiesMap = new HashMap<String, NameEntity>();
			List<Morpheme> morphemes = null;
			List<NameEntity> nameEntities = null;

			for (Map<String, Object> sentence : sentences) {
				// ????????? ????????? ?????? ?????? ??? ??????
				List<Map<String, Object>> morphologicalAnalysisResult = (List<Map<String, Object>>) sentence
						.get("morp");

				for (Map<String, Object> morphemeInfo : morphologicalAnalysisResult) {
					String lemma = (String) morphemeInfo.get("lemma");
					Morpheme morpheme = morphemesMap.get(lemma);
					if (morpheme == null) {
						morpheme = new Morpheme(lemma, (String) morphemeInfo.get("type"), 1);
						morphemesMap.put(lemma, morpheme);
					} else {
						morpheme.count = morpheme.count + 1;
					}
				}

				// ????????? ?????? ?????? ?????? ??? ??????
				List<Map<String, Object>> nameEntityRecognitionResult = (List<Map<String, Object>>) sentence.get("NE");
				for (Map<String, Object> nameEntityInfo : nameEntityRecognitionResult) {
					String name = (String) nameEntityInfo.get("text");
					NameEntity nameEntity = nameEntitiesMap.get(name);
					if (nameEntity == null) {
						nameEntity = new NameEntity(name, (String) nameEntityInfo.get("type"), 1);
						nameEntitiesMap.put(name, nameEntity);
					} else {
						nameEntity.count = nameEntity.count + 1;
					}
				}
			}

			if (0 < morphemesMap.size()) {
				morphemes = new ArrayList<Morpheme>(morphemesMap.values());
				morphemes.sort((morpheme1, morpheme2) -> {
					return morpheme2.count - morpheme1.count;
				});
			}

			if (0 < nameEntitiesMap.size()) {
				nameEntities = new ArrayList<NameEntity>(nameEntitiesMap.values());
				nameEntities.sort((nameEntity1, nameEntity2) -> {
					return nameEntity2.count - nameEntity1.count;
				});
			}

			// ???????????? ??? ???????????? ????????? ?????? ????????? ????????? ?????? ( ?????? 5??? )
			morphemes.stream().filter(morpheme -> {
				return morpheme.type.equals("NNG") || morpheme.type.equals("NNB");
			}).limit(5).forEach(morpheme -> {

				System.out.println("[??????] " + morpheme.text + " (" + morpheme.count + ")");

				finalDtoList.add("??????");
				finalDtoList.add(morpheme.text);

				return;
			});
			morphemes.stream().filter(morpheme -> {
				return morpheme.type.equals("NNP");
			}).limit(5).forEach(morpheme -> {

				System.out.println("[????????????] " + morpheme.text + " (" + morpheme.count + ")");

				finalDtoList.add("????????????");
				finalDtoList.add(morpheme.text);

				return;
			});

			morphemes.stream().filter(morpheme -> {
				return morpheme.type.equals("NP");
			}).limit(5).forEach(morpheme -> {

				System.out.println("[?????????] " + morpheme.text + " (" + morpheme.count + ")");

				finalDtoList.add("?????????");
				finalDtoList.add(morpheme.text);

				return;
			});

			// ???????????? ??? ???????????? ????????? ?????? ????????? ????????? ?????? ( ?????? 5??? )

			morphemes.stream().filter(morpheme -> {
				return morpheme.type.equals("VV");
			}).limit(5).forEach(morpheme -> {
				System.out.println("[??????] " + morpheme.text + " (" + morpheme.count + ")");

				finalDtoList.add("??????");
				finalDtoList.add(morpheme.text);
				return;
			});

			morphemes.stream().filter(morpheme -> {
				return morpheme.type.equals("MM") || morpheme.type.equals("MAG") || morpheme.type.equals("MAJ");
			}).limit(5).forEach(morpheme -> {
				System.out.println("[?????????] " + morpheme.text + " (" + morpheme.count + ")");

				finalDtoList.add("?????????");
				finalDtoList.add(morpheme.text);
				return;
			});

			morphemes.stream().filter(morpheme -> {
				return morpheme.type.equals("JKS") || morpheme.type.equals("JKC") || morpheme.type.equals("JKG")
						|| morpheme.type.equals("JKO") || morpheme.type.equals("JKB") || morpheme.type.equals("JKV")
						|| morpheme.type.equals("JKQ") || morpheme.type.equals("JX") || morpheme.type.equals("JC");
			}).limit(5).forEach(morpheme -> {
				System.out.println("[??????] " + morpheme.text + " (" + morpheme.count + ")");

				finalDtoList.add("??????");
				finalDtoList.add(morpheme.text);
				return;
			});

		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(finalDtoList);
		return finalDtoList;
	}

	// ????????? SSL ??????
	public void sslTrustAllCerts() {
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };
		SSLContext sc;
		try {
			sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@GetMapping("/oneWord")
	public List<WordItemDto> oneWord(@RequestParam String q) {

		List<WordItemDto> wordItemDtos = new ArrayList<>();

		BufferedReader brWord = null;
		// DocumentBuilderFactory ??????
		DocumentBuilderFactory factoryWord = DocumentBuilderFactory.newInstance();
		factoryWord.setNamespaceAware(true);
		DocumentBuilder builderWord;
		Document docWord = null;
		sslTrustAllCerts(); // SSL ????????? ??????

		try {
			// OpenApi??????

			System.out.println("======== ????????? ???????????? API ?????? ========");

			String word = null; // example ????????? ?????? word
			String urlStrWord = "https://krdict.korean.go.kr/api/search?" + "key=FAFF5405FEE6910E824515B8B9A2BA08" // ?????????
					+ "&q=" + q; // ?????? ?????????
			URL urlWord = new URL(urlStrWord);
			HttpURLConnection urlconnectionWord = (HttpURLConnection) urlWord.openConnection();

			// ?????? ??????
			brWord = new BufferedReader(new InputStreamReader(urlconnectionWord.getInputStream(), "UTF-8"));
			String resultWord = "";
			String lineWord;
			while ((lineWord = brWord.readLine()) != null) {
				resultWord = resultWord + lineWord.trim();// result = URL??? XML??? ?????? ???
				// System.out.println(line);
			}

			// xml ????????????
			InputSource isWord = new InputSource(new StringReader(resultWord)); // ????????? api ???????????????
			builderWord = factoryWord.newDocumentBuilder();
			docWord = builderWord.parse(isWord);
			XPathFactory xpathFactoryWord = XPathFactory.newInstance();
			XPath xpathWord = xpathFactoryWord.newXPath();
			XPathExpression exprWord = xpathWord.compile("/channel/item"); // xpath??? ???????????? ????????????
			NodeList nodeListWord = (NodeList) exprWord.evaluate(docWord, XPathConstants.NODESET);
			for (int i = 0; i < nodeListWord.getLength(); i++) {
				NodeList childWord = nodeListWord.item(i).getChildNodes();
				WordItemDto wordItemDto = new WordItemDto();
				List<WordSenseDto> wordSenseDtos = new ArrayList<>();
				for (int j = 0; j < childWord.getLength(); j++) {
					Node nodeWord = childWord.item(j);
					if (nodeWord.getNodeName() == "target_code") {
						String target_codeString = nodeWord.getTextContent().toString();
						int target_codeInt = Integer.parseInt(target_codeString);
						wordItemDto.setTarget_code(target_codeInt);
					} else if (nodeWord.getNodeName() == "word") {
						wordItemDto.setWord(nodeWord.getTextContent());
						word = nodeWord.getTextContent();
					} else if (nodeWord.getNodeName() == "pronunciation") {
						wordItemDto.setPronunciation(nodeWord.getTextContent());
					} else if (nodeWord.getNodeName() == "pos") {
						wordItemDto.setPos(nodeWord.getTextContent());
					} else if (nodeWord.getNodeName() == "sense") {
						WordSenseDto wordSenseDto = new WordSenseDto();
						StringBuilder definition = new StringBuilder();

						for (int h = 1; h < nodeWord.getTextContent().length(); h++) {
							definition.append(nodeWord.getTextContent().charAt(h));
						}

						wordSenseDto.setSense_order(nodeWord.getTextContent().charAt(0) - 48); // Char to Integer ->
						// ASCII 48 ?????????
						wordSenseDto.setDefinition(definition.toString());
						wordSenseDtos.add(wordSenseDto);
						wordItemDto.setSense(wordSenseDtos);
					}
				}
				wordItemDtos.add(wordItemDto);
			}
			return wordItemDtos;
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		return wordItemDtos;
	}

	@GetMapping("/myPage")
	public List<String> myPage() {
		List<String> vocabularyList = new ArrayList<>();
		Object sessionId = loginId.getAttribute("userId");
		String userId = sessionId.toString();

		vocabularyList = iVocabularyNoteDao.showWord(userId);

		return vocabularyList;
	}

	@GetMapping("/addToNote")
	public void addToNote(@RequestParam String q, @RequestParam String p) {
		Object sessionId = loginId.getAttribute("userId");
		String userId = sessionId.toString();

		// ???????????? ????????? ?????? ????????? ??????
		if (iVocabularyNoteDao.checkWord(userId, q) == 0) {
			iVocabularyNoteDao.addWord(userId, q, p);
			/* ????????? ???????????? ??????????????? ?????????????????? ???????????? ???????????? ???????????? */
		} else {
			/* ????????? ???????????? ??????????????? ?????????????????? ???????????? ???????????? ???????????? */
		}

		if (iWordRankingDao.wordRankingSelect(q) == 0) {
			iWordRankingDao.wordRankingInsert(q);
			iWordRankingDao.wordRankingUp(q);
		} else {
			iWordRankingDao.wordRankingUp(q);
		}
	}

	// ??????????????? ?????? ??????
	@GetMapping("/deleteFromNote")
	public List<String> deleteFromNote(@RequestParam String word) {
		List<String> vocabularyList = new ArrayList<>();
		Object sessionId = loginId.getAttribute("userId");
		String userId = sessionId.toString();

		iVocabularyNoteDao.deleteWord(userId, word);

		vocabularyList = iVocabularyNoteDao.showWord(userId);

		if (iWordRankingDao.wordRankingSelect(word) == 1) {
			iWordRankingDao.wordRankingDelete(word);
		} else {
			iWordRankingDao.wordRankingDown(word);
		}
		return vocabularyList;
	}

	// ?????? ??????
	@GetMapping("/wordQuiz")
	public List<QuizTemplateDto> wordQuiz() {
		List<QuizTemplateDto> quizTemplateList = new ArrayList<>();
		QuizTemplateDto quizTemplate = new QuizTemplateDto();

		// Object sessionId = loginId.getAttribute("userid");
		// String userId = sessionId.toString();

		List<VocabularyNoteDto> vocabularyNoteList = null;
		vocabularyNoteList = iVocabularyNoteDao.getVocabularynote();

		for (int n = 0; n < 40; n++) {
			// System.out.println("WORD " + n + " : " +
			// vocabularyNoteList.get(n).getWord());
			// System.out.println("MEAN " + n + " : " +
			// vocabularyNoteList.get(n).getMean());
			if (n % 4 == 0) {
				quizTemplate = new QuizTemplateDto();
				quizTemplate.setWord(vocabularyNoteList.get(n).getWord());
				quizTemplate.setWord_mean(vocabularyNoteList.get(n).getMean());
			} else if (n % 4 == 1) {
				quizTemplate.setWrong_answer1(vocabularyNoteList.get(n).getMean());
			} else if (n % 4 == 2) {
				quizTemplate.setWrong_answer2(vocabularyNoteList.get(n).getMean());
			} else {
				quizTemplate.setWrong_answer3(vocabularyNoteList.get(n).getMean());
				quizTemplateList.add(quizTemplate);
			}

		}

		System.out.println(quizTemplateList);

		return quizTemplateList;
	}

	// ?????? ?????? user_ranking table??? ????????????
	@GetMapping("/getQuizResult")
	public void getQuizResult(@RequestParam int score) {

		Object id = (String) loginId.getAttribute("userId");
		String userId = (String) id;
		int point = score;

		if (point > 0)
			iUserRankingDao.getQuizResult(userId, point);
	}

	// ?????? ???????????? ??????
	@PostMapping("/wordRank")
	public List<List<Object>> wordRank() {
		List<List<Object>> wordRankingList = new ArrayList<>();
		List<Object> wordRankingWord = new ArrayList<>();
		List<Object> wordRankingPoint = new ArrayList<>();

		System.out.println("wordRank ??????");

		wordRankingWord.add(iWordRankingDao.wordRankingWord());
		wordRankingList.add(wordRankingWord);
		wordRankingPoint.add(iWordRankingDao.wordRankingPoint());
		wordRankingList.add(wordRankingPoint);

		return wordRankingList;
	}

	@PostMapping("/userRank")
	public List<List<Object>> userRank(KakaoDto kakaoDto) {
		// System.out.println("???????????? : " + kakaoDto.getNickName());

		List<List<Object>> userRankingList = new ArrayList<>();
		List<Object> userRankingUserId = new ArrayList<>();
		List<Object> userRankingImage = new ArrayList<>();
		List<Object> userRankingPoint = new ArrayList<>();

		System.out.println("userRank ??????");

		userRankingUserId.add(iUserRankingDao.userRankingUserId());
		userRankingList.add(userRankingUserId);
		userRankingPoint.add(iUserRankingDao.userRankingPoint());
		userRankingList.add(userRankingPoint);
		System.out.println("point : " + userRankingPoint);
		userRankingImage.add(iUserRankingDao.userRankingImage());
		userRankingList.add(userRankingImage);

		// userRankingUserId.add(iUserRankingDao.userRankingPoint());
		// userRankingList.add(userRankingPoint);

		return userRankingList;
	}
}