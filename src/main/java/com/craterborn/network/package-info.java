/**
 * Rede (SimpleChannel do Forge). Regra: o servidor é a autoridade. Servidor -> cliente só leva dados para a
 * HUD; cliente -> servidor só leva PEDIDOS, que são validados do zero no servidor.
 */
package com.craterborn.network;
