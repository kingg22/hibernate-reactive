/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.session.impl;

import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.reactive.boot.spi.ReactiveMetadataImplementor;
import org.hibernate.reactive.coroutines.Coroutines;
import org.hibernate.reactive.coroutines.impl.CoroutinesSessionFactoryImpl;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.mutiny.impl.MutinySessionFactoryImpl;
import org.hibernate.reactive.stage.Stage;
import org.hibernate.reactive.stage.impl.StageSessionFactoryImpl;

/**
 * A Hibernate {@link org.hibernate.SessionFactory} that can be
 * unwrapped to produce a {@link Stage.SessionFactory} or a
 * {@link Mutiny.SessionFactory} or a {@link Coroutines.SessionFactory}.
 */
public class ReactiveSessionFactoryImpl extends SessionFactoryImpl {

	public ReactiveSessionFactoryImpl(MetadataImplementor bootMetamodel, SessionFactoryOptions options, BootstrapContext bootstrapContext) {
		super( new ReactiveMetadataImplementor( bootMetamodel ), options, bootstrapContext );
	}

	@Override
	public <T> T unwrap(Class<T> type) {
		if ( type.isAssignableFrom( Stage.SessionFactory.class ) ) {
			return type.cast( new StageSessionFactoryImpl( this ) );
		}
		if ( type.isAssignableFrom( Mutiny.SessionFactory.class ) ) {
			return type.cast( new MutinySessionFactoryImpl( this ) );
		}
		if ( type.isAssignableFrom( Coroutines.SessionFactory.class ) ) {
			return type.cast( new CoroutinesSessionFactoryImpl( this ) );
		}
		return super.unwrap( type );
	}
}
