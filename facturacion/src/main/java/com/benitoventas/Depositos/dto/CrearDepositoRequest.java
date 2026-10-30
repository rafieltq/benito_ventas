package com.benitoventas.Depositos.dto;

import java.math.BigDecimal;

public record CrearDepositoRequest(String referencia, BigDecimal monto) {}
