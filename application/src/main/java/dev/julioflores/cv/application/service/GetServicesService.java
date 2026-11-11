package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetServicesUseCase;
import dev.julioflores.cv.application.port.out.SiteContentRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.ServiceOffer;
import java.util.List;

public class GetServicesService implements GetServicesUseCase {

    private final SiteContentRepository content;

    public GetServicesService(SiteContentRepository content) {
        this.content = content;
    }

    @Override
    public List<ServiceOffer> execute(Language language) {
        return content.services(language);
    }
}
