package dev.inboxbridge.service.destination;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GmailInvalidAttachmentExceptionTest {

    @Test
    void recognizesTypedRejectionThroughWrapperExceptions() {
        RuntimeException wrapped = new RuntimeException(
                "Destination invocation failed",
                new GmailInvalidAttachmentException(400, "{\"error\":{\"message\":\"Invalid attachment\"}}"));

        assertTrue(GmailInvalidAttachmentException.isPolicyRejection(wrapped));
    }

    @Test
    void recognizesProductionShapedGenericPolicyRejection() {
        IllegalStateException rejection = new IllegalStateException("""
                Failed to import Gmail message: 400 - {
                  "error": {
                    "message": "Invalid attachment. Please check https://support.google.com/mail/answer/6590.",
                    "status": "INVALID_ARGUMENT"
                  }
                }""");

        assertTrue(GmailInvalidAttachmentException.isPolicyRejection(rejection));
    }

    @Test
    void doesNotClassifyUnrelatedGmailBadRequestAsAttachmentPolicyRejection() {
        IllegalStateException invalidLabel = new IllegalStateException("""
                Failed to import Gmail message: 400 - {
                  "error": {
                    "message": "Invalid label",
                    "status": "INVALID_ARGUMENT"
                  }
                }""");

        assertFalse(GmailInvalidAttachmentException.isPolicyRejection(invalidLabel));
    }

    @Test
    void requiresTheGmailImportBadRequestBoundaryForGenericMatching() {
        IllegalStateException unrelatedLog = new IllegalStateException(
                "External service returned 500: Invalid attachment. "
                        + "Please check https://support.google.com/mail/answer/6590.");

        assertFalse(GmailInvalidAttachmentException.isPolicyRejection(unrelatedLog));
    }
}
