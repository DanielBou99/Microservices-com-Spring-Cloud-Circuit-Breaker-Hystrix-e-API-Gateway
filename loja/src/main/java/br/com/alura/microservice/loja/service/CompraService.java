package br.com.alura.microservice.loja.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

import br.com.alura.microservice.loja.client.FornecedorClient;
import br.com.alura.microservice.loja.client.TransportadorClient;
import br.com.alura.microservice.loja.controller.dto.CompraDTO;
import br.com.alura.microservice.loja.controller.dto.InfoEntregaDTO;
import br.com.alura.microservice.loja.controller.dto.InfoFornecedorDTO;
import br.com.alura.microservice.loja.controller.dto.InfoPedidoDTO;
import br.com.alura.microservice.loja.controller.dto.VoucherDTO;
import br.com.alura.microservice.loja.modelo.Compra;
import br.com.alura.microservice.loja.modelo.CompraState;
import br.com.alura.microservice.loja.repository.CompraRepository;

@Service
public class CompraService {
	
	
	private static final Logger LOG = LoggerFactory.getLogger(CompraService.class);

	@Autowired
	private FornecedorClient fornecedorClient;
	
	@Autowired
	private TransportadorClient transportadorClient;
	
	@Autowired
	private CompraRepository compraRepository;
	
	@HystrixCommand(threadPoolKey = "GetByIdThreadPool")
	public Compra getById(Long id) {	
		return compraRepository.findById(id).orElse(new Compra());
	}
	
	@HystrixCommand(fallbackMethod = "realizaCompraFallback",
			threadPoolKey = "realizaCompraThreadPool")
	public Compra realizaCompra(CompraDTO compra) {
		
		Compra compraSalva = new Compra();
		compraSalva.setState(CompraState.RECEBIDO);
		compraRepository.save(compraSalva);
		compra.setCompraId(compraSalva.getId());		
		compraSalva.setEnderecoDestino(compra.getEndereco().toString());

		final String estado = compra.getEndereco().getEstado();
		
		LOG.info("Buscando informações do fornecedor de {}.", estado);
		InfoFornecedorDTO info = fornecedorClient.getInfoPorEstado(compra.getEndereco().getEstado());
		LOG.info("Realizando um pedido.");
		InfoPedidoDTO pedido = fornecedorClient.realizaPedido(compra.getItens());
		compraSalva.setPedidoId(pedido.getId());
		compraSalva.setTempoDePreparo(pedido.getTempoDePreparo());
		
		compraSalva.setState(CompraState.PEDIDO_REALIZADO);
		compraRepository.save(compraSalva);
		
		InfoEntregaDTO entregaDto = new InfoEntregaDTO();
		entregaDto.setPedidoId(pedido.getId());
		entregaDto.setDataParaEntrega(LocalDate.now().plusDays(pedido.getTempoDePreparo()));
		entregaDto.setEnderecoOrigem(info.getEndereco());
		entregaDto.setEnderecoDestino(compra.getEndereco().toString());
		VoucherDTO voucher = transportadorClient.reservaEntrega(entregaDto);
		compraSalva.setDataParaEntrega(voucher.getPrevisaoParaEntrega());
		compraSalva.setVoucher(voucher.getNumero());
		
		compraSalva.setState(CompraState.RESERVA_ENTREGA_REALIZADA);
		compraRepository.save(compraSalva);

		return compraSalva;
	}
	
	public Compra realizaCompraFallback(CompraDTO compra) {
		
		if(compra.getCompraId() != null) {
			return compraRepository.findById(compra.getCompraId()).get();
		}
		
		Compra compraFallback = new Compra();
		compraFallback.setEnderecoDestino(compra.getEndereco().toString());
		return compraFallback;
	}

}