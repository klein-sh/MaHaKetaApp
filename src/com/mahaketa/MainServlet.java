package com.mahaketa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.mahaketa.Attachment;
import com.mahaketa.Article;

/**
 * The entry servlet
 */
@WebServlet(
		name = "mainServlet",
		urlPatterns={"/articles"},
		loadOnStartup=1
)
public class MainServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    
	private volatile int ARTICLE_ID_SEQUENCE = 1;
	private Map<Integer, Article> articles = new LinkedHashMap<>();

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String action = request.getParameter("action");
		if (null == action)
			action = "list";
		switch(action) {
			case "create":
				this.showArticleForm(response);
				break;
			case "view":
				this.viewArticle(request, response);
				break;
			case "download":
				this.downloadAttachment(request, response);
				break;
			case "list":
			default:
				this.listArticles(response);
		}
	}

	private void downloadAttachment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String idString = request.getParameter("articleId");
        Article article = this.getArticle(idString, response);
        if(article == null)
            return;

        String name = request.getParameter("attachment");
        if(name == null)
        {
            response.sendRedirect("tickets?action=view&ticketId=" + idString);
            return;
        }

        Attachment attachment = article.getAttachment();
        if(attachment == null)
        {
            response.sendRedirect("tickets?action=view&ticketId=" + idString);
            return;
        }

        response.setHeader("Content-Disposition",
                "attachment; filename=" + attachment.getName());
        response.setContentType("application/octet-stream");

        ServletOutputStream stream = response.getOutputStream();
        stream.write(attachment.getContents());
	}

	private void listArticles(HttpServletResponse response) throws IOException {
		PrintWriter writer = this.writeHeader(response);

	    writer.append("<h2>Articles</h2>\r\n");
	    writer.append("<a href=\"articles?action=create\">Create an Article")
	    	  .append("</a><br/><br/>\r\n");

        if(this.articles.size() == 0)
            writer.append("<i>There are no articles in the system.</i>\r\n");
        else {
            for(int id : this.articles.keySet()) {
                String idString = Integer.toString(id);
                Article article = this.articles.get(id);
                writer.append("Article #").append(idString)
                      .append(": <a href=\"articles?action=view&ticketId=")
                      .append(idString).append("\">").append(article.getHeadline())
                      .append("</a> (customer: ").append(article.getBody())
                      .append(")<br/>\r\n");
            }
        }

        this.writeFooter(writer);
	}

	private void viewArticle(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String idString = request.getParameter("articleId");
        Article article = this.getArticle(idString, response);
        if(article == null)
            return;

        PrintWriter writer = this.writeHeader(response);

        writer.append("<h2>Article #").append(idString)
              .append(": ").append(article.getHeadline()).append("</h2>\r\n");
        writer.append(article.getBody()).append("<br/><br/>\r\n");

        writer.append("Attachments: ");
        Attachment attachment = article.getAttachment();
        writer.append("<a href=\"tickets?action=download&ticketId=")
              .append(idString).append("&attachment=")
              .append(attachment.getName()).append("\">")
              .append(attachment.getName()).append("</a>");
        writer.append("<br/><br/>\r\n")
        	  .append("<a href=\"tickets\">Return to list tickets</a>\r\n");

        this.writeFooter(writer);
		
	}

	private Article getArticle(String idString, HttpServletResponse response) throws IOException {
        if(idString == null || idString.length() == 0)
        {
            response.sendRedirect("articles");
            return null;
        }

        try
        {
            Article article = this.articles.get(Integer.parseInt(idString));
            if(article == null)
            {
                response.sendRedirect("articles");
                return null;
            }
            return article;
        }
        catch(Exception e)
        {
            response.sendRedirect("article");
            return null;
        }
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String action = request.getParameter("action");
		if (action == null)
			action = "list";
		switch(action) {
			case "create":
				this.createArticle(request, response);
				break;
			case "list":
			default:
				response.sendRedirect("articles");
		}
	}

	private void showArticleForm(HttpServletResponse response) throws IOException {
		PrintWriter writer = this.writeHeader(response);
		
		writer.append("<h2>Create an Article</h2>\r\n");
        writer.append("<form method=\"POST\" action=\"articles\" ")
              .append("enctype=\"multipart/form-data\">\r\n");
        writer.append("<input type=\"hidden\" name=\"action\" ")
              .append("value=\"create\"/>\r\n");
        writer.append("Article Headline<br/>\r\n");
        writer.append("<input type=\"text\" name=\"headline\"/><br/><br/>\r\n");
        writer.append("Body<br/>\r\n");
        writer.append("<textarea name=\"body\" rows=\"5\" cols=\"30\">")
              .append("</textarea><br/><br/>\r\n");
        writer.append("<b>Attachments</b><br/>\r\n");
        writer.append("<input type=\"file\" name=\"file1\"/><br/><br/>\r\n");
        writer.append("<input type=\"submit\" value=\"Submit\"/>\r\n");
        writer.append("</form>\r\n");
		
        this.writeFooter(writer);
	}

	private void createArticle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		Article article = new Article();
		article.setHeadline(request.getParameter("headline"));
		article.setBody(request.getParameter("body"));
		
		Part filePart = request.getPart("file1");
		if (filePart != null && filePart.getSize() > 0) {
			Attachment attachment = this.processAttachment(filePart);
			if (attachment != null)
				article.setAttachment(attachment);
		}
		
		int id;
		synchronized (this) {
			id = this.ARTICLE_ID_SEQUENCE++;
			this.articles.put(id, article);
		}
		
		response.sendRedirect("articles?action=view&&articleId=" + id);
		
	}
	
	private Attachment processAttachment(Part filePart) throws IOException {
        InputStream inputStream = filePart.getInputStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        int read;
        final byte[] bytes = new byte[1024];

        while((read = inputStream.read(bytes)) != -1)
        {
            outputStream.write(bytes, 0, read);
        }

        Attachment attachment = new Attachment();
        attachment.setName(filePart.getSubmittedFileName());
        attachment.setContents(outputStream.toByteArray());

        return attachment;
	}

	private PrintWriter writeHeader(HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
	    response.setCharacterEncoding("UTF-8");

	    PrintWriter writer = response.getWriter();
		writer.append("<!DOCTYPE html>\r\n")
        	  .append("<html>\r\n")
        	  .append("    <head>\r\n")
        	  .append("        <title>MaHaKetaApp</title>\r\n")
        	  .append("    </head>\r\n")
        	  .append("    <body>\r\n");
		
		return writer;
	}

	private void writeFooter(PrintWriter writer) {
		writer.append("    </body>\r\n").append("</html>\r\n");
	}
		
}


