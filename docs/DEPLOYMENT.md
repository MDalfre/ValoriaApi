# Implantação e custo-benefício

## Arquitetura recomendada

```text
Internet
  -> HTTPS / reverse proxy
     -> valoria-web (Nginx + React)
        -> /api -> valoria-api (Spring Boot)
                    -> PostgreSQL OpenMU
                    -> diretório de backups
  -> portas do ConnectServer/GameServers
```

Frontend e API ficam na rede Docker `openmu-network`. Somente Nginx e portas necessárias do jogo devem ser publicados. PostgreSQL e a porta interna `8090` da API não devem ser expostos à internet.

## Primeira opção: Hostinger VPS

Para o estágio inicial, uma VPS única tende a entregar o melhor custo-benefício e a menor complexidade operacional. O KVM 2 anunciado para o Brasil oferece 2 vCPUs, 8 GB de RAM, 100 GB NVMe e 8 TB de tráfego por preço promocional; confirme sempre preço de renovação e disponibilidade no momento da contratação na [página oficial da Hostinger](https://www.hostinger.com/vps/servers/brazil).

Vantagens:

- datacenter brasileiro e baixa latência para o público principal;
- Docker Compose igual ao ambiente atual;
- custo previsível e recursos concentrados.

Desvantagens:

- aplicação, jogo e banco compartilham a mesma máquina;
- escalabilidade e alta disponibilidade exigem trabalho manual;
- o operador é responsável por atualizações, firewall e recuperação.

## Segunda opção: AWS

Para manter Docker Compose com pouca mudança, Amazon Lightsail é mais simples que montar ECS/RDS no início. O plano Linux de 4 GB aparece a US$ 24/mês e o de 2 GB a US$ 12/mês na [tabela oficial de bundles](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-bundles.html). O serviço também oferece opções separadas de containers, banco e storage na [página de preços](https://aws.amazon.com/lightsail/pricing/).

Vantagens:

- caminho gradual para banco gerenciado, storage e CDN;
- métricas, snapshots e integração com o ecossistema AWS;
- melhor caminho de escala e redundância.

Desvantagens:

- custo total tende a subir ao separar banco, snapshots, tráfego e storage;
- operação, rede e cobrança são mais complexas;
- região e rota até jogadores brasileiros precisam ser testadas.

## Recomendação inicial

Começar com Hostinger KVM 2 ou VPS equivalente de 8 GB, mantendo:

- snapshots do provedor;
- backups PostgreSQL fora da própria VPS;
- Cloudflare para DNS, TLS e proteção do site;
- monitoramento externo de disponibilidade;
- restauração testada em ambiente separado.

Migrar componentes para AWS quando métricas reais mostrarem necessidade de alta disponibilidade, autoscaling ou banco gerenciado.

## Checklist de produção

1. Clonar `ValoriaApi` e `ValoriaWeb` como pastas irmãs.
2. Criar `/opt/valoria/secrets/api.env` com permissão `0600`.
3. Gerar `JWT_SECRET_BASE64` aleatório e nunca enviá-lo ao Git.
4. Configurar firewall para 80/443 e portas estritamente necessárias do game.
5. Usar proxy HTTPS; definir `FRONTEND_ORIGIN` com o domínio final.
6. Manter `BACKUP_RESTORE_ENABLED=false` até homologar restauração.
7. Montar backups e testar download; depois conceder escrita apenas se upload for habilitado.
8. Configurar cópia externa dos backups e política de retenção.
9. Executar `docker compose config` antes do primeiro `up`.
10. Validar cadastro, login, ranking e acesso administrativo.

