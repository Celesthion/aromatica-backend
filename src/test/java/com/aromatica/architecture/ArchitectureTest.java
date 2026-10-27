package com.aromatica.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Инварианты архитектуры из ARCHITECTURE.md, проверяемые по байткоду. */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aromatica");
    }

    /** Верхние папки (config, security, common, modules) и бизнес-области между собой — без циклов. */
    @Test
    void areasHaveNoCycles() {
        slices().matching("com.aromatica.(*)..").should().beFreeOfCycles().check(classes);
        slices().matching("com.aromatica.modules.(*)..").should().beFreeOfCycles().check(classes);
    }

    /** Внутри области модули тоже зависят в одну сторону. */
    @Test
    void modulesInsideAreaHaveNoCycles() {
        slices().matching("com.aromatica.modules.(*).(*)..").should().beFreeOfCycles().check(classes);
    }

    /**
     * Сервис владеет своим репозиторием: чужие данные — только через сервисы их модуля.
     * Модуль — третий уровень пакета (inventory.stock, finance.money…).
     */
    @Test
    void repositoriesAreUsedOnlyInsideTheirModule() {
        classes().that().resideInAPackage("..repository..")
                .should(new ArchCondition<>("be used only inside their own module") {
                    @Override
                    public void check(
                            JavaClass repository,
                            ConditionEvents events
                    ) {
                        String module = moduleOf(repository);
                        repository.getDirectDependenciesToSelf().stream()
                                .filter(d -> !moduleOf(d.getOriginClass()).equals(module))
                                .forEach(d -> events.add(SimpleConditionEvent.violated(d, d.getDescription())));
                    }
                })
                .check(classes);
    }

    /**
     * Таблицы модуля в запросах (QueryDSL Q-классы его сущностей) читает только сам модуль.
     * Исключение — inventory.report: сторона чтения, её проекции соединяют таблицы нескольких
     * модулей ради фильтра и сортировки в базе. Писать в чужие таблицы она не может — только читать.
     */
    @Test
    void entityQueriesStayInsideTheirModule() {
        classes().that().resideInAPackage("..model..").and().haveSimpleNameStartingWith("Q")
                .should(new ArchCondition<>("be used only inside their own module or by the report read side") {
                    @Override
                    public void check(
                            JavaClass query,
                            ConditionEvents events
                    ) {
                        String module = moduleOf(query);
                        query.getDirectDependenciesToSelf().stream()
                                .filter(d -> !moduleOf(d.getOriginClass()).equals(module))
                                .filter(d -> !d.getOriginClass().getPackageName().startsWith("com.aromatica.modules.inventory.report"))
                                .forEach(d -> events.add(SimpleConditionEvent.violated(d, d.getDescription())));
                    }
                })
                .check(classes);
    }

    /** Доменный слой работает с сущностями и командами; экранные DTO — забота мапперов и сервисов чтения. */
    @Test
    void domainDoesNotKnowDtos() {
        noClasses().that().resideInAnyPackage("..service..", "..usecase..")
                .and().haveSimpleNameNotContaining("QueryService")
                .should().dependOnClassesThat().resideInAPackage("..dtos..")
                .check(classes);
    }

    /** Endpoint только зовёт маппер: ни один DTO не собирается в нём конструктором. */
    @Test
    void endpointsDoNotBuildDtos() {
        noClasses().that().resideInAPackage("..endpoint..")
                .should().callConstructorWhere(target(owner(resideInAPackage("..dtos.."))))
                .check(classes);
    }

    /** Реакции на события — в пакетах listener, мапперы — в пакетах mapper. */
    @Test
    void listenersAndMappersLiveInTheirPackages() {
        methods().that().areAnnotatedWith(EventListener.class)
                .should().beDeclaredInClassesThat().resideInAPackage("..listener..")
                .check(classes);
        classes().that().areAnnotatedWith(Mapper.class)
                .should().resideInAPackage("..mapper..")
                .check(classes);
    }

    /** Модуль: modules.<область>.<модуль> у бизнеса, security.auth у входа. */
    private static String moduleOf(JavaClass c) {
        String[] parts = c.getPackageName().split("\\.");
        int depth = parts.length > 2 && parts[2].equals("modules") ? 5 : 4;
        return parts.length >= depth ? String.join(".", Arrays.copyOf(parts, depth)) : c.getPackageName();
    }

    /**
     * Слои зависят только вниз: common ← security ← masterdata ← inventory ← finance ← pos
     * ← workspace, а config собирает всё и сам никому не нужен. Склад не знает о кассе,
     * деньги — о ленте действий, бизнес — о настройке Spring.
     */
    @Test
    void areasDependOnlyDownwards() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("config").definedBy("com.aromatica.config..")
                .layer("common").definedBy("com.aromatica.common..")
                .layer("security").definedBy("com.aromatica.security..")
                .layer("masterdata").definedBy("com.aromatica.modules.masterdata..")
                .layer("inventory").definedBy("com.aromatica.modules.inventory..")
                .layer("finance").definedBy("com.aromatica.modules.finance..")
                .layer("pos").definedBy("com.aromatica.modules.pos..")
                .layer("workspace").definedBy("com.aromatica.modules.workspace..")
                .whereLayer("config").mayNotBeAccessedByAnyLayer()
                .whereLayer("workspace").mayNotBeAccessedByAnyLayer()
                .whereLayer("pos").mayOnlyBeAccessedByLayers("workspace")
                .whereLayer("finance").mayOnlyBeAccessedByLayers("pos", "workspace")
                .whereLayer("inventory").mayOnlyBeAccessedByLayers("finance", "pos", "workspace")
                .whereLayer("masterdata").mayOnlyBeAccessedByLayers("inventory", "finance", "pos", "workspace")
                .whereLayer("security")
                .mayOnlyBeAccessedByLayers("config", "masterdata", "inventory", "finance", "pos", "workspace")
                .check(classes);
    }

    @Test
    void preAuthorizeOnlyOnControllers() {
        classes().that().areAnnotatedWith(PreAuthorize.class)
                .should().resideInAPackage("..controller..").check(classes);
        methods().that().areAnnotatedWith(PreAuthorize.class)
                .should().beDeclaredInClassesThat().resideInAPackage("..controller..").check(classes);
    }

    @Test
    void controllersTalkOnlyToEndpoints() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..repository..")
                .check(classes);
    }

    /**
     * Имя DTO уникально по всему API: Swagger называет схему простым именем класса,
     * и одноимённые записи разных модулей слились бы в одну схему.
     */
    @Test
    void dtoNamesAreUniqueAcrossModules() {
        classes().that().resideInAPackage("..dtos..")
                .should(new ArchCondition<>("have a simple name unique across dtos packages") {
                    @Override
                    public void check(
                            JavaClass dto,
                            ConditionEvents events
                    ) {
                        classes.stream()
                                .filter(other -> other.getPackageName().contains(".dtos")
                                        && !other.equals(dto)
                                        && other.getSimpleName().equals(dto.getSimpleName()))
                                .forEach(other -> events.add(SimpleConditionEvent.violated(dto,
                                        dto.getName() + " и " + other.getName() + " называются одинаково")));
                    }
                })
                .check(classes);
    }

    /** Контроллер — тонкий адаптер HTTP: держит только endpoint'ы своего ресурса. */
    @Test
    void controllersHoldOnlyEndpoints() {
        fields().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                .should().haveRawType(simpleNameEndingWith("Endpoint"))
                .check(classes);
    }

    /** Деньги — в тыйынах (long), количества — BigDecimal: двоичная дробь копит ошибку округления. */
    @Test
    void noFloatingPointNumbers() {
        DescribedPredicate<JavaClass> floatingPoint = DescribedPredicate.describe("double or float",
                type -> type.isEquivalentTo(double.class) || type.isEquivalentTo(float.class)
                        || type.isEquivalentTo(Double.class) || type.isEquivalentTo(Float.class));
        noFields().should().haveRawType(floatingPoint).check(classes);
        noMethods().should().haveRawReturnType(floatingPoint).check(classes);
        noClasses().should().dependOnClassesThat(floatingPoint).check(classes);
    }

    @Test
    void repositoriesAreNotUsedAboveServices() {
        noClasses().that().resideInAnyPackage("..controller..", "..endpoint..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .check(classes);
    }

    /** Граница транзакции — endpoint; refresh намеренно без неё (см. AuthEndpointImpl). */
    @Test
    void endpointMethodsAreTransactional() {
        methods().that().arePublic()
                .and().areDeclaredInClassesThat().resideInAPackage("..endpoint.impl..")
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("EndpointImpl")
                .and().doNotHaveName("refresh")
                .should().beAnnotatedWith(Transactional.class)
                .check(classes);
    }

    /**
     * Сервис читает по умолчанию: @Transactional(readOnly = true) на классе,
     * пишущий метод объявляет @Transactional сам.
     */
    @Test
    void servicesAreReadOnlyByDefault() {
        classes().that().resideInAPackage("..service.impl..")
                .and().areAnnotatedWith(Service.class)
                .should().beAnnotatedWith(new DescribedPredicate<JavaAnnotation<?>>("@Transactional(readOnly = true)") {
                    @Override
                    public boolean test(JavaAnnotation<?> annotation) {
                        return annotation.getRawType().isEquivalentTo(Transactional.class)
                                && annotation.as(Transactional.class).readOnly();
                    }
                })
                .check(classes);
    }

    /**
     * Запрос — контракт API: его переводит маппер, сервис получает сущность или доменную
     * команду. Фильтры поиска (пакет search) — исключение, их читают SearchService.
     */
    @Test
    void servicesDoNotDependOnRequests() {
        noClasses().that().resideInAnyPackage("..service..", "..usecase..")
                .should().dependOnClassesThat(simpleNameEndingWith("Request")
                        .and(resideInAnyPackage("com.aromatica.."))
                        .and(resideOutsideOfPackage("..search..")))
                .check(classes);
    }

    /**
     * Сборка DTO и перевод запросов — в endpoint через маппер, сервис о мапперах не знает.
     * Исключение — сервисы чтения (*QueryService): их результат и есть проекция для экрана.
     */
    @Test
    void servicesDoNotUseMappers() {
        noClasses().that().resideInAnyPackage("..service..", "..usecase..")
                .and().haveSimpleNameNotContaining("QueryService")
                .should().dependOnClassesThat().resideInAPackage("..mapper..")
                .check(classes);
    }

    /** Поиск списков живёт в отдельных SearchService, доменные сервисы его не держат; сервисы чтения — могут. */
    @Test
    void searchMethodsOnlyInSearchServices() {
        methods().that().haveNameStartingWith("search")
                .and().areDeclaredInClassesThat().resideInAPackage("..service..")
                .and().areDeclaredInClassesThat().haveSimpleNameNotContaining("QueryService")
                .should().beDeclaredInClassesThat().haveSimpleNameContaining("SearchService")
                .check(classes);
    }
}
