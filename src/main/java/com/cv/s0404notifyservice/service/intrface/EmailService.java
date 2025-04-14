package com.cv.s0404notifyservice.service.intrface;

import com.cv.s0402notifyservicepojo.dto.MessageDto;
import com.cv.s0402notifyservicepojo.dto.RecipientDto;

import java.util.concurrent.CompletableFuture;

public interface EmailService {

    CompletableFuture<Void> sendEmail(MessageDto message, RecipientDto recipient) throws Exception;
}
