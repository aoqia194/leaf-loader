/*
 * Copyright 2025 aoqia, FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.aoqia.leaf.loader.api.metadata.version;

import java.util.Collection;
import java.util.function.Predicate;

import dev.aoqia.leaf.loader.api.Version;
import dev.aoqia.leaf.loader.api.VersionParsingException;
import dev.aoqia.leaf.loader.impl.util.version.VersionPredicateParser;

public interface VersionPredicate extends Predicate<Version> {
	/**
	 * Get all terms that have to be satisfied for this predicate to match.
	 *
	 * @return Required predicate terms, empty if anything matches
	 */
	Collection<? extends PredicateTerm> getTerms();

	/**
	 * Get the version interval representing the matched versions.
	 *
	 * @return Covered version interval or null if nothing
	 */
	VersionInterval getInterval();

	interface PredicateTerm {
		VersionComparisonOperator getOperator();
		Version getReferenceVersion();
	}

	static VersionPredicate parse(String predicate) throws VersionParsingException {
		return VersionPredicateParser.parse(predicate);
	}

	static Collection<VersionPredicate> parse(Collection<String> predicates) throws VersionParsingException {
		return VersionPredicateParser.parse(predicates);
	}
}
