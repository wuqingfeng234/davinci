/*
 * <<
 *  Davinci
 *  ==
 *  Copyright (C) 2016 - 2019 EDP
 *  ==
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *        http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  >>
 *
 */

package edp.core.utils;

import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.JSON;
import edp.core.exception.ServerException;
import edp.core.model.MailContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.annotation.Resource;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static edp.davinci.core.common.Constants.EMAIL_DEFAULT_TEMPLATE;

@Component
@Slf4j
public class MailUtils {

    @Resource
    private TemplateEngine templateEngine;
    @Value("${mail.app.key}")
    private String appKey;

    @Value("${mail.app.secret}")
    private String appSecret;

    @Value("${mail.server.url}")
    private String url;

    private static final String MAIL_TEXT_KEY = "text";
    private static final String MAIL_HTML_KEY = "html";


    public void sendMail(MailContent mailContent, Logger customLogger) throws ServerException {

        if (mailContent == null) {
            log.error("Email content is null");
            throw new ServerException("Email content is null");
        }

        if (StringUtils.isEmpty(mailContent.getSubject())) {
            if (customLogger != null) {
                customLogger.error("Email subject cannot be empty");
            }
            throw new ServerException("Email subject cannot be empty");
        }

        if (null == mailContent.getTo() || mailContent.getTo().length < 1) {
            if (customLogger != null) {
                log.error("Email receiving address(to) cannot be empty");
            }
            throw new ServerException("Email receiving address cannot be empty");
        }

        boolean emptyAttachments = CollectionUtils.isEmpty(mailContent.getAttachments());
        String mailContentTemplate = null;
        Context context = new Context();
        switch (mailContent.getMailContentType()) {
            case TEXT:
                if (StringUtils.isEmpty(mailContent.getContent()) && emptyAttachments) {
                    throw new ServerException("Email content cannot be empty");
                }

                context.setVariable(MAIL_TEXT_KEY, mailContent.getContent());
                mailContentTemplate = EMAIL_DEFAULT_TEMPLATE;
                break;
            case HTML:
                if (StringUtils.isEmpty(mailContent.getHtmlContent()) && emptyAttachments) {
                    throw new ServerException("Email content cannot be empty");
                }

                context.setVariable(MAIL_HTML_KEY, mailContent.getHtmlContent());
                mailContentTemplate = EMAIL_DEFAULT_TEMPLATE;
                break;
            case TEMPLATE:
                if (StringUtils.isEmpty(mailContent.getTemplate()) && emptyAttachments) {
                    throw new ServerException("Email content cannot be empty");
                }
                if (!CollectionUtils.isEmpty(mailContent.getTemplateContent())) {
                    mailContent.getTemplateContent().forEach(context::setVariable);
                }
                mailContentTemplate = mailContent.getTemplate();
                break;
        }

        try {
            String contentHtml = templateEngine.process(mailContentTemplate, context);
            sendMail(mailContent.getTo(), mailContent.getSubject(), contentHtml);

        } catch (Exception e) {
            if (customLogger != null) {
                customLogger.error("Send mail error:{}", e.getMessage());
            }
            throw new ServerException(e.getMessage());
        }
    }

    public void sendMail(String[] mailTo, String subject, String mailContent) throws MalformedURLException, NoSuchAlgorithmException, InvalidKeyException {
        Map<String, String> headerMap = HmacSignUtil.createSignHeader(appKey, appSecret, url, "post");

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);

            httpPost.setHeader("Content-Type", "application/json");

            httpPost.setHeader("Date", headerMap.get("Date"));
            httpPost.setHeader("X-HMAC-ACCESS-KEY", headerMap.get("X-HMAC-ACCESS-KEY"));
            httpPost.setHeader("X-HMAC-ALGORITHM", headerMap.get("X-HMAC-ALGORITHM"));
            httpPost.setHeader("X-HMAC-SIGNATURE", headerMap.get("X-HMAC-SIGNATURE"));

            String uuid = UUID.randomUUID().toString();
            Map<String, Serializable> mail = createMailMap(uuid, mailTo, subject, mailContent, true);
            httpPost.setEntity(new StringEntity(JSON.toJSONString(mail), ContentType.APPLICATION_JSON));
            CloseableHttpResponse response = httpclient.execute(httpPost);
            EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("exception is ", e);
        }
    }

    private Map<String, Serializable> createMailMap(String emailNo, String[] toAddress, String subject, String content, boolean isHtml) {
        Map<String, Serializable> mail = new HashMap<>();
        mail.put("emailNo", emailNo);
        mail.put("toAddress", toAddress);
        mail.put("subject", subject);
        mail.put("content", content);
        mail.put("isHtml", isHtml);
        return mail;
    }
}
