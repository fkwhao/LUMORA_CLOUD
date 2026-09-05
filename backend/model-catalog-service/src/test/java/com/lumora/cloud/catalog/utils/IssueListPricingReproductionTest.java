package com.lumora.cloud.catalog.utils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.catalog.domain.dto.pricing.ModelVersionInput;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class IssueListPricingReproductionTest {
 @Test void lm013CatalogAllowsAllCachedZeroFeeCombination() throws Exception {
  var input=new ObjectMapper().readValue("""
  {"displayName":"Test","upstreamModel":"test","contextWindow":128000,"maxOutputTokens":4096,
   "supportsTools":true,"costCurrency":"USD","uncachedInputCostPerMillion":1,"cachedInputCostPerMillion":0,
   "cacheCreationInputCostPerMillion":1,"outputCostPerMillion":0,
   "uncachedInputQuotaPerMillion":1,"cachedInputQuotaPerMillion":0,"cacheCreationInputQuotaPerMillion":1,
   "outputQuotaPerMillion":0,"minimumRequestQuota":0}
  """,ModelVersionInput.class);
  try(var factory=Validation.buildDefaultValidatorFactory()){assertThat(factory.getValidator().validate(input)).isEmpty();}
  var accepted=new CatalogInputMapper().values(input);
  System.out.println("REPRO LM-013 catalog accepted uncached="+accepted.inputQuotaPerMillion()+" cached="+accepted.cacheReadQuotaPerMillion()+" output="+accepted.outputQuotaPerMillion()+" minimum="+accepted.minimumRequestQuota());
  assertThat(accepted.cacheReadQuotaPerMillion()).isZero();assertThat(accepted.outputQuotaPerMillion()).isZero();
 }
}
