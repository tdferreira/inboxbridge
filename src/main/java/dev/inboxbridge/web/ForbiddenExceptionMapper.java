package dev.inboxbridge.web;

import dev.inboxbridge.dto.ApiError;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Override
    public Response toResponse(ForbiddenException exception) {
        String message = exception.getMessage();
        String details = ApiErrorDetails.deepestMessage(exception);
        return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(ApiErrorCodes.resolve(details.isBlank() ? message : details, 403), message, details))
                .build();
    }
}
