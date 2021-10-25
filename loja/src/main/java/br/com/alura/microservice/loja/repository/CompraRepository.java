package br.com.alura.microservice.loja.repository;


import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import br.com.alura.microservice.loja.modelo.Compra;


@Repository
public interface CompraRepository extends CrudRepository<Compra, Long>{

}
