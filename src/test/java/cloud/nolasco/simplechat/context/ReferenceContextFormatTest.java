package cloud.nolasco.simplechat.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ReferenceContextFormatTest {

  private static final String INVENTORY = "/context/project-inventory.md";
  private static final Pattern AREA = Pattern.compile("([\\d.]+) m2");
  private static final Pattern PRICE = Pattern.compile("\\$([\\d,]+(?:\\.\\d{2})?) MXN");

  @Test
  void loaderContextFilesExist() {
    List.of("developer-profile.md", "project-overview.md", "project-brochure.md", "project-inventory.md")
        .forEach(name -> assertThat(new ClassPathResource("/context/" + name).exists())
            .as("%s is hardcoded in ReferenceDocsLoader", name)
            .isTrue());
  }

  @Test
  void residenceRecordsAreComplete() throws IOException {
    List<Residence> residences = readResidences();

    assertThat(residences).hasSize(14);
    assertThat(residences).extracting(Residence::id).doesNotHaveDuplicates();

    for (Residence residence : residences) {
      assertThat(residence.fields())
          .as("%s fields", residence.id())
          .containsKeys("Status", "Typology", "Land", "Built");
      residence.fields().forEach((key, value) ->
          assertThat(value).as("%s %s", residence.id(), key).isNotBlank());

      assertThat(parseArea(residence.fields().get("Land"))).as("%s land", residence.id()).isPositive();
      assertThat(parseArea(residence.fields().get("Built"))).as("%s built", residence.id()).isPositive();

      if ("Available".equals(residence.status())) {
        assertThat(residence.fields()).as("%s fields", residence.id()).containsKey("Price");
        assertThat(parsePrice(residence.fields().get("Price"))).as("%s price", residence.id()).isPositive();
      } else {
        assertThat(residence.status()).as("%s status", residence.id()).isEqualTo("Reserved");
        assertThat(residence.fields()).as("%s fields", residence.id())
            .containsKey("Notes")
            .doesNotContainKey("Price");
      }
    }
  }

  @Test
  void availableResidencesSupportVerifiedSuperlatives() throws IOException {
    List<Residence> available = readResidences().stream()
        .filter(residence -> "Available".equals(residence.status()))
        .toList();

    assertThat(available).hasSize(11);

    Residence cheapest = available.stream().min(Comparator.comparing(Residence::price)).orElseThrow();
    assertThat(cheapest.id()).isEqualTo("B-18");
    assertThat(cheapest.price()).isEqualByComparingTo(new BigDecimal("9340000.00"));

    Residence priciest = available.stream().max(Comparator.comparing(Residence::price)).orElseThrow();
    assertThat(priciest.id()).isEqualTo("C-16");
    assertThat(priciest.price()).isEqualByComparingTo(new BigDecimal("14750000.00"));

    Residence largestLand = available.stream().max(Comparator.comparing(Residence::land)).orElseThrow();
    assertThat(largestLand.id()).isEqualTo("B-41");
    assertThat(largestLand.land()).isEqualByComparingTo(new BigDecimal("513"));

    Residence largestBuilt = available.stream().max(Comparator.comparing(Residence::built)).orElseThrow();
    assertThat(largestBuilt.id()).isEqualTo("A-14");
    assertThat(largestBuilt.built()).isEqualByComparingTo(new BigDecimal("418.90"));
  }

  private static List<Residence> readResidences() throws IOException {
    String text = new String(
        new ClassPathResource(INVENTORY).getContentAsByteArray(), StandardCharsets.UTF_8);

    return text.lines()
        .filter(line -> line.startsWith("Residence "))
        .map(ReferenceContextFormatTest::parseResidence)
        .toList();
  }

  private static Residence parseResidence(String line) {
    String[] parts = line.split("\\s*\\|\\s*");
    String id = parts[0].substring("Residence ".length()).trim();
    Map<String, String> fields = Arrays.stream(parts)
        .skip(1)
        .map(part -> part.split(":", 2))
        .collect(Collectors.toMap(field -> field[0].trim(), field -> field[1].trim()));

    return new Residence(id, fields);
  }

  private static BigDecimal parseArea(String value) {
    Matcher matcher = AREA.matcher(value);
    assertThat(matcher.matches()).as("area value '%s'", value).isTrue();
    return new BigDecimal(matcher.group(1));
  }

  private static BigDecimal parsePrice(String value) {
    Matcher matcher = PRICE.matcher(value);
    assertThat(matcher.matches()).as("price value '%s'", value).isTrue();
    return new BigDecimal(matcher.group(1).replace(",", ""));
  }

  private record Residence(String id, Map<String, String> fields) {
    String status() {
      return fields.get("Status");
    }

    BigDecimal land() {
      return parseArea(fields.get("Land"));
    }

    BigDecimal built() {
      return parseArea(fields.get("Built"));
    }

    BigDecimal price() {
      return parsePrice(fields.get("Price"));
    }
  }
}
