package com.aromatica.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Правила стиля, которых не видно в байткоде: их проверяем по исходникам. */
class SourceStyleTest {

    private static final List<Path> SOURCES = List.of(Path.of("src/main/java"), Path.of("src/test/java"));

    /** Полное имя класса в коде: java.util.List, jakarta.validation.constraints.NotBlank… */
    private static final Pattern QUALIFIED_NAME =
            Pattern.compile("\\b(java|javax|jakarta|org|com|lombok|tools)\\.[a-z]\\w*(\\.[a-z]\\w*)*\\.[A-Z]\\w*");

    /** Вызов Assertions.assertThat(...) без статического импорта. */
    private static final Pattern QUALIFIED_ASSERTION = Pattern.compile("\\bAssertions\\.assert");

    /** Объявление тестового метода. */
    private static final Pattern TEST_NAME = Pattern.compile("^\\s+void \\w+\\(");

    /** Версия зависимости числом, а не свойством из properties. */
    private static final Pattern LITERAL_VERSION = Pattern.compile("^\\s*<version>\\d");

    @Test
    void classesAreImportedNotQualified() {
        assertThat(codeLines(line -> QUALIFIED_NAME.matcher(line).find()))
                .as("полные имена классов вместо импорта")
                .isEmpty();
    }

    @Test
    void assertionsAreStaticallyImported() {
        assertThat(codeLines(line -> QUALIFIED_ASSERTION.matcher(line).find()))
                .as("assertThat без статического импорта")
                .isEmpty();
    }

    /** Класс своего пакета виден без импорта; такой импорт — след переезда пакета. */
    @Test
    void ownPackageIsNotImported() {
        List<String> imports = SOURCES.stream()
                .flatMap(SourceStyleTest::javaFiles)
                .flatMap(SourceStyleTest::ownPackageImports)
                .toList();
        assertThat(imports).as("импорт из своего пакета").isEmpty();
    }

    /** Тест модуля называется «метод_поведение»; тесты архитектуры называют инвариант. */
    @Test
    void unitTestsAreNamedMethodUnderscoreBehavior() {
        List<String> misnamed = javaFiles(Path.of("src/test/java"))
                .filter(file -> !file.toString().contains("/architecture/"))
                .flatMap(SourceStyleTest::testsWithoutUnderscore)
                .toList();
        assertThat(misnamed).as("тест без схемы метод_поведение").isEmpty();
    }

    /** Версии — только параметрами в properties; единственное число — родитель spring-boot-starter-parent и сам проект. */
    @Test
    void dependencyVersionsAreProperties() throws IOException {
        List<String> literal = Files.readAllLines(Path.of("pom.xml")).stream()
                .filter(line -> LITERAL_VERSION.matcher(line).find())
                .toList();
        assertThat(literal).as("версии числом в pom.xml").hasSizeLessThanOrEqualTo(2);
    }

    /** Строки кода (без import, package и комментариев), подходящие под условие, — «файл:строка: текст». */
    private static List<String> codeLines(Predicate<String> violation) {
        return SOURCES.stream()
                .flatMap(SourceStyleTest::javaFiles)
                .flatMap(file -> violations(file, violation))
                .toList();
    }

    private static Stream<Path> javaFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<String> violations(
            Path file,
            Predicate<String> violation
    ) {
        try {
            List<String> lines = Files.readAllLines(file);
            return IntStream.range(0, lines.size())
                    .filter(i -> isCode(lines.get(i)) && violation.test(lines.get(i)))
                    .mapToObj(i -> file + ":" + (i + 1) + ": " + lines.get(i).strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<String> ownPackageImports(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            String pkg = lines.stream().filter(l -> l.startsWith("package ")).findFirst()
                    .map(l -> l.substring("package ".length(), l.indexOf(';')))
                    .orElse("");
            Pattern own = Pattern.compile("^import " + Pattern.quote(pkg) + "\\.[A-Z]\\w*;");
            return lines.stream().filter(l -> own.matcher(l).find()).map(l -> file + ": " + l);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<String> testsWithoutUnderscore(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return IntStream.range(0, lines.size())
                    .filter(i -> lines.get(i).strip().equals("@Test"))
                    .mapToObj(i -> declarationAfter(lines, i))
                    .filter(line -> !line.contains("_"))
                    .map(line -> file + ": " + line.strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Объявление метода под @Test: другие аннотации между ними пропускаем. */
    private static String declarationAfter(
            List<String> lines,
            int testAnnotation
    ) {
        int i = testAnnotation + 1;
        while (!TEST_NAME.matcher(lines.get(i)).find()) {
            i++;
        }
        return lines.get(i);
    }

    private static boolean isCode(String line) {
        String s = line.strip();
        return !(s.startsWith("import ") || s.startsWith("package ") || s.startsWith("*")
                || s.startsWith("/*") || s.startsWith("//"));
    }
}
