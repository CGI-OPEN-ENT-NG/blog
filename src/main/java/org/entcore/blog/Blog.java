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


package org.entcore.blog;

import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.json.JsonArray;
import org.entcore.blog.controllers.BlogController;
import org.entcore.blog.controllers.FoldersController;
import org.entcore.blog.controllers.PostController;
import org.entcore.blog.core.constants.Field;
import org.entcore.blog.events.BlogSearchingEvents;
import org.entcore.blog.explorer.BlogExplorerPlugin;
import org.entcore.blog.explorer.PostExplorerPlugin;
import org.entcore.blog.security.BlogResourcesProvider;
import org.entcore.blog.services.BlogService;
import org.entcore.blog.services.PostService;
import org.entcore.blog.services.impl.BlogRepositoryEvents;
import org.entcore.blog.services.impl.DefaultBlogService;
import org.entcore.blog.services.impl.DefaultPostService;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.BaseServer;
import org.entcore.common.mongodb.MongoDbConf;

import java.util.List;
import java.util.stream.Collectors;

public class Blog extends BaseServer {

    public static final String APPLICATION = "blog";
    public static final String BLOG_TYPE = "blog";
    public static final String POSTS_COLLECTION = "posts";
    public static final String BLOGS_COLLECTION = "blogs";
    BlogExplorerPlugin blogPlugin;

    @Override
    public void start() throws Exception {
        super.start();
        setDefaultResourceFilter(new BlogResourcesProvider());

        MongoDbConf.getInstance().setCollection("blogs");

        EventStoreFactory eventStoreFactory = EventStoreFactory.getFactory();
        eventStoreFactory.setVertx(vertx);

        setRepositoryEvents(new BlogRepositoryEvents(vertx));

        if (config.getBoolean("searching-event", true)) {
            List<String> searchingOnList = config.getJsonArray("searching-on", new JsonArray().add(Field.POST)).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            setSearchingEvents(new BlogSearchingEvents(searchingOnList));
        }

        final MongoDbConf conf = MongoDbConf.getInstance();
        conf.setCollection(BLOGS_COLLECTION);
        conf.setResourceIdLabel("id");

        blogPlugin = BlogExplorerPlugin.create(securedActions);
        final PostExplorerPlugin postPlugin = blogPlugin.postPlugin();
        final MongoDb mongo = MongoDb.getInstance();
        final PostService postService = new DefaultPostService(mongo, config.getInteger("post-search-word-min-size", 4), PostController.LIST_ACTION, postPlugin);
        final BlogService blogService = new DefaultBlogService(mongo, postService, config.getInteger("blog-paging-size", 30),
                config.getInteger("blog-search-word-min-size", 4), blogPlugin);
        addController(new BlogController(mongo, blogService, postService));
        addController(new PostController(blogService, postService));
        addController(new FoldersController("blogsFolders"));
        blogPlugin.start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (blogPlugin != null) {
            blogPlugin.stop();
        }
    }
}
