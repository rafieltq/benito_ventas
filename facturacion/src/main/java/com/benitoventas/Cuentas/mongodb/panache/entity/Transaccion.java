package com.benitoventas.Cuentas.mongodb.panache.entity;

import com.benitoventas.Cuentas.mongodb.panache.entity.TipoTransaccion;
import java.math.BigDecimal;
import java.time.Instant;
import org.bson.types.ObjectId;


public class Transaccion {
  String id;
  TipoTransaccion tipo;
  BigDecimal monto;
  Instant fecha;
  String descripcion;
  ObjectId referenciaId;
}
