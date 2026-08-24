/**
 * O que a rede NAO manda e precisa ser inferido: velocidade (Kalman) e posse
 * (geometria da boca). Todo numero daqui e estimativa, nunca medida.
 *
 * <p>Zona: ESTAVEL. Depende de: ajuste, core, model, mundo, proto.
 *
 * <p>Estas duas linhas nao sao enfeite: teste.Autoteste as le e falha se o
 * codigo divergir delas. Ver a secao Arquitetura do README.
 */
package percepcao;
