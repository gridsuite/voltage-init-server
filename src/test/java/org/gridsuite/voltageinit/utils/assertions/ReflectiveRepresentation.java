package org.gridsuite.voltageinit.utils.assertions;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.assertj.core.presentation.Representation;
import org.assertj.core.presentation.StandardRepresentation;
import org.jetbrains.annotations.NotNull;

/**
 * AssertJ {@link Representation} for types having no {@link Object#toString() toString()} implemented
 */
@AutoService(Representation.class)
@Slf4j
public class ReflectiveRepresentation extends StandardRepresentation {
    /**
     * {@inheritDoc}
     */
    @Override
    protected String fallbackToStringOf(@NotNull final Object object) {
        try {
            if (Object.class.equals(object.getClass().getMethod("toString").getDeclaringClass())) {
                return ToStringBuilder.reflectionToString(object);
            }
        } catch (NoSuchMethodException e) {
            log.warn("Error while analysing object class", e);
        }
        return object.toString();
    }
}
