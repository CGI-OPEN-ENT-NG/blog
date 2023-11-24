/*
 * Copyright © "Open Digital Education" (SAS “WebServices pour l’Education”), 2014
 *
 * This program is published by "Open Digital Education" (SAS “WebServices pour l’Education”).
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https: //opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.blog.events;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Either.Right;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.blog.core.constants.Field;
import org.entcore.blog.search.BlogSearch;
import org.entcore.blog.search.PostSearch;
import org.entcore.common.search.SearchingEvents;

import java.util.List;
import java.util.stream.Collectors;

public class BlogSearchingEvents implements SearchingEvents {

    private static final Logger log = LoggerFactory.getLogger(BlogSearchingEvents.class);
    private final List<String> searchingOnList;

    public BlogSearchingEvents(List<String> searchingOnList) {
        this.searchingOnList = searchingOnList;
    }

    @Override
    public void searchResource(List<String> appFilters, String userId, JsonArray groupIds, final JsonArray searchWords, final Integer page, final Integer limit,
                               final JsonArray columnsHeader, final String locale, final Handler<Either<String, JsonArray>> handler) {
        if (appFilters.contains(BlogSearchingEvents.class.getSimpleName())) {
            List<String> groupIdList = groupIds.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toList());

            List<String> searchWordsList = searchWords.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toList());

            PostSearch postSearch = new PostSearch();
            BlogSearch blogSearch = new BlogSearch();
            Future<JsonArray> postFuture = this.searchingOnList.contains(Field.POST) ? postSearch.get(userId, groupIdList, searchWordsList, page, limit) : Future.succeededFuture(new JsonArray());

            Future<JsonArray> blogFuture = this.searchingOnList.contains(Field.BLOG) ? blogSearch.get(userId, groupIdList, searchWordsList, page, limit) : Future.succeededFuture(new JsonArray());

            CompositeFuture.all(postFuture, blogFuture)
                    .onSuccess(compositeFuture -> {
                        JsonArray postFormatted = postSearch.formatSearchResult(postFuture.result(), columnsHeader);
                        JsonArray blogFormatted = blogSearch.formatSearchResult(blogFuture.result(), columnsHeader);

                        handler.handle(new Right<>(new JsonArray().addAll(blogFormatted).addAll(postFormatted)));
                    })
                    .onFailure(throwable -> {
                        log.error(String.format("[blog@%s::searchResource] Failed to search resource %s:%s", this.getClass().getSimpleName(), throwable.getClass().getSimpleName(), throwable.getMessage()));
                        handler.handle(new Either.Left<>(throwable.getMessage()));
                    });
        } else {
            handler.handle(new Right<>(new JsonArray()));
        }
    }
}
