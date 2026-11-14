package com.nexo.service.pdi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexo.entity.TipoAcaoPdi;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Factory Method das ações do PDI: todo tipo tem exatamente um criador. */
class AcaoPdiFactoryTest {

    @Test
    void tipoSemCriadorImpedeASubida() {
        assertThatThrownBy(() -> new AcaoPdiFactory(List.of(new AcaoDescritiva())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRILHA");
    }

    @Test
    void doisCriadoresParaOMesmoTipoImpedemASubida() {
        CriadorDeAcao deTrilha = mock(CriadorDeAcao.class);
        when(deTrilha.tipos()).thenReturn(EnumSet.of(TipoAcaoPdi.TRILHA, TipoAcaoPdi.MENTORIA));

        assertThatThrownBy(() -> new AcaoPdiFactory(List.of(deTrilha, new AcaoDescritiva())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MENTORIA");
    }
}
