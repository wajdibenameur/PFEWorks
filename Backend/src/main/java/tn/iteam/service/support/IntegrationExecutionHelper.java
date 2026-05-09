package tn.iteam.service.support;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import tn.iteam.exception.IntegrationException;
import tn.iteam.service.SourceAvailabilityService;

@Component
public class IntegrationExecutionHelper {

    public <T> T execute(
            SourceAvailabilityService availabilityService,
            Logger log,
            String source,
            String label,
            String warnMessageTemplate,
            String unexpectedErrorMessage,
            T fallback,
            SupplierWithException<T> action
    ) {
        try {
            T result = action.get();
            availabilityService.markAvailable(source);
            return result;
        } catch (IntegrationException ex) {
            availabilityService.markUnavailable(source, ex.getMessage());
            log.warn(warnMessageTemplate, label, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            availabilityService.markUnavailable(source, ex.getMessage());
            log.error("{} [{}]", unexpectedErrorMessage, label, ex);
            throw ex;
        }
    }

    public void execute(
            SourceAvailabilityService availabilityService,
            Logger log,
            String source,
            String label,
            String warnMessageTemplate,
            String unexpectedErrorMessage,
            RunnableWithException action
    ) {
        try {
            action.run();
            availabilityService.markAvailable(source);
        } catch (IntegrationException ex) {
            availabilityService.markUnavailable(source, ex.getMessage());
            log.warn(warnMessageTemplate, label, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            availabilityService.markUnavailable(source, ex.getMessage());
            log.error(unexpectedErrorMessage.formatted(label), ex);
            throw ex;
        }
    }

    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get();
    }

    @FunctionalInterface
    public interface RunnableWithException {
        void run();
    }
}
