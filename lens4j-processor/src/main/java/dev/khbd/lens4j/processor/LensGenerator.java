package dev.khbd.lens4j.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dev.khbd.lens4j.core.Lenses;
import dev.khbd.lens4j.core.ReadLens;
import dev.khbd.lens4j.core.ReadWriteLens;
import dev.khbd.lens4j.core.annotations.LensType;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import java.util.Map;

/**
 * Lens generator.
 *
 * @author Alexey_Bodyak
 */
public class LensGenerator {

    /**
     * Generate factory source file.
     *
     * @param factoryMeta factory meta
     * @return generated java file
     */
    public JavaFile generate(FactoryMeta factoryMeta) {
        return JavaFile.builder(factoryMeta.getPackageName(), makeType(factoryMeta)).build();
    }

    private TypeSpec makeType(FactoryMeta factoryMeta) {
        TypeSpec.Builder builder = TypeSpec.classBuilder(factoryMeta.getFactoryName());
        builder.addModifiers(factoryMeta.getFactoryModifiers().toArray(new Modifier[0]));
        builder.addAnnotation(makeGeneratedAnnotation());
        builder.addMethod(makeConstructor());
        for (LensMeta lensMeta : factoryMeta.getLenses()) {
            builder.addField(makeLens(lensMeta));
        }
        return builder.build();
    }

    private AnnotationSpec makeGeneratedAnnotation() {
        return AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", LensProcessor.class.getCanonicalName())
                .build();
    }

    private FieldSpec makeLens(LensMeta lensMeta) {
        return FieldSpec.builder(makeLensType(lensMeta), lensMeta.getLensName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                .initializer(makeExpression(lensMeta))
                .build();
    }

    private CodeBlock makeExpression(LensMeta lensMeta) {
        CodeBlock.Builder builder = CodeBlock.builder();

        if (lensMeta.isSinglePart()) {
            builder.add(makeCodeBlockLens(lensMeta.getFirstLensPart(), lensMeta.getLensType()));
            return builder.build();
        }

        builder.add(makeCodeBlockLens(lensMeta.getFirstLensPart(), LensType.READ));
        for (LensPartMeta part : lensMeta.getLensPartsWithoutEnds()) {
            builder.add(".andThen($L)", makeCodeBlockLens(part, LensType.READ));
        }
        builder.add(".andThen($L)", makeCodeBlockLens(lensMeta.getLastLensPart(), lensMeta.getLensType()));
        return builder.build();
    }

    private CodeBlock makeCodeBlockLens(LensPartMeta lensPartMeta, LensType lensType) {
        Map<String, Object> params = Map.of(
                "lenses", ClassName.get(Lenses.class),
                "baseType", TypeName.get(lensPartMeta.getSourceType()),
                "fieldName", StringUtils.capitalize(lensPartMeta.getPropertyName())
        );
        return CodeBlock.builder()
                .addNamed(makeMethodNameByTypeLens(lensType), params)
                .build();
    }

    private String makeMethodNameByTypeLens(LensType lensType) {
        return lensType == LensType.READ ? "$lenses:T.readLens($baseType:T::get$fieldName:L)"
                : "$lenses:T.readWriteLens($baseType:T::get$fieldName:L, $baseType:T::set$fieldName:L)";
    }

    private TypeName makeLensType(LensMeta lensMeta) {
        if (lensMeta.getLensType() == LensType.READ) {
            return makeLensReadType(lensMeta);
        }
        return makeLensReadWriteType(lensMeta);
    }

    private TypeName makeLensReadType(LensMeta lensMeta) {
        return ParameterizedTypeName.get(
                ClassName.get(ReadLens.class),
                TypeName.get(lensMeta.getFirstLensPart().getSourceType()),
                TypeName.get(lensMeta.getLastLensPart().getPropertyType())
        );
    }

    private TypeName makeLensReadWriteType(LensMeta lensMeta) {
        return ParameterizedTypeName.get(
                ClassName.get(ReadWriteLens.class),
                TypeName.get(lensMeta.getFirstLensPart().getSourceType()),
                TypeName.get(lensMeta.getLastLensPart().getPropertyType())
        );
    }

    private MethodSpec makeConstructor() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build();
    }
}
