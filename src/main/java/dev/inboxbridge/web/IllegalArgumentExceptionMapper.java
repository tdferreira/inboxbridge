package dev.inboxbridge.web;

import dev.inboxbridge.dto.ApiError;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        String message = exception.getMessage();
        String details = ApiErrorDetails.deepestMessage(exception);
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(ApiErrorCodes.resolve(details.isBlank() ? message : details, 400), message, details))
                .build();
    }
}
